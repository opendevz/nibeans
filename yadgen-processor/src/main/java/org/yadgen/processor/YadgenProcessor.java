package org.yadgen.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yadgen.IBean;
import org.yadgen.internal.BeanProviderService;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

/**
 * Processes classes annotated with {@link org.yadgen.IBean} and generated default implementations for them.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
@SupportedOptions({ YadgenProcessor.OPT_SOURCE_PACKAGES, YadgenProcessor.OPT_TARGET_CLASS })
public class YadgenProcessor extends AbstractProcessor {
	private static final Logger LOG = LoggerFactory.getLogger(YadgenProcessor.class);

	public static final String OPT_SOURCE_PACKAGES = "srcpackages";
	public static final String OPT_TARGET_CLASS = "tgtclass";

	private static final Class<? extends Annotation> BEAN_CLASS = IBean.class;
	private static final Class<?> SERVICE_CLASS = BeanProviderService.class;
	private static final Pattern NAME_PATTERN = Pattern.compile("^(\\w+(\\.\\w+)*)\\.(\\w+)$");

	private final Set<String> packagesToScan = new HashSet<>();
	private String targetPackage;
	private String targetClass;
	private Mustache template;
	private final List<ImplClassInfo> generatedClasses = new ArrayList<>();

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		// Extract the names of the packages to scan
		String packages = processingEnv.getOptions().get(OPT_SOURCE_PACKAGES);
		if (packages != null) {
			String[] rawValues = packages.split(",");
			for (String value : rawValues) {
				value = value.trim();
				if (!value.isEmpty()) {
					packagesToScan.add(value);
				}
			}
		}
		if (packagesToScan.isEmpty()) {
			throw new IllegalArgumentException("Missing value for option -A" + OPT_SOURCE_PACKAGES);
		}
		// Extract the name of the class
		String targetClassOption = processingEnv.getOptions().get(OPT_TARGET_CLASS);
		if (targetClassOption != null) {
			Matcher m = NAME_PATTERN.matcher(targetClassOption);
			if (m.matches()) {
				targetPackage = m.group(1);
				targetClass = m.group(3);
			}
		}
		if (targetClass == null) {
			throw new IllegalArgumentException("Missing or bad value for option -A" + OPT_TARGET_CLASS);
		}
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Collections.singleton(BEAN_CLASS.getName());
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.errorRaised()) {
			return false;
		}
		try {
			if (roundEnv.processingOver()) {
				return generateResults();
			}
			// Get which classes to scan
			boolean claimed = false;
			for (Element element : roundEnv.getElementsAnnotatedWith(BEAN_CLASS)) {
				// Only interfaces are processed
				if (element.getKind() != ElementKind.INTERFACE) {
					continue;
				}
				TypeElement intfElement = (TypeElement) element;
				// Make sure the interface is inside one of the packages explicitly specified
				Element packageElm = intfElement.getEnclosingElement();
				if (packageElm == null || packageElm.getKind() != ElementKind.PACKAGE
						|| !packagesToScan.contains(((PackageElement) packageElm).getQualifiedName().toString())) {
					continue;
				}
				// Process this interface
				ImplClassInfo clsInfo = processInterafce(intfElement);
				if (clsInfo != null) {
					generatedClasses.add(clsInfo);
					claimed = true;
				}
			}
			return claimed;
		} catch (IOException e) {
			LOG.warn(e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Process a single bean interface.
	 */
	private ImplClassInfo processInterafce(TypeElement intfElement) throws IOException {
		Map<String, Property> properties = new LinkedHashMap<>();
		int fullProperties = 0;
		final Types typeUtils = processingEnv.getTypeUtils();
		DeclaredType intfType = typeUtils.getDeclaredType(intfElement);
		// Inspect the elements
		for (Element enclosedElement : intfElement.getEnclosedElements()) {
			if (enclosedElement.getKind() != ElementKind.METHOD) {
				continue;
			}
			ExecutableElement methodElement = (ExecutableElement) enclosedElement;
			final String name = methodElement.getSimpleName().toString();
			// Check the name
			String propName = null;
			if ((propName = getPropetyName(name, "get")) != null) {
				// Getters should have no arguments
				if (!methodElement.getParameters().isEmpty()) {
					return null;
				}
				// Match return type
				TypeMirror propType = methodElement.getReturnType();
				Property property = properties.get(propName);
				if (property == null) {
					property = new Property();
					property.name = propName;
					property.propType = propType;
					properties.put(propName, property);
				} else if (property.getter == null && typeUtils.isSameType(property.propType, propType)) {
					++fullProperties;
				} else {
					return null;
				}
				property.getter = methodElement;
			} else if ((propName = getPropetyName(name, "set")) != null) {
				// Setters should have no return values and a single argument
				if (methodElement.getReturnType().getKind() != TypeKind.VOID
						|| methodElement.getParameters().size() != 1) {
					return null;
				}
				// Setters should have a single argument with correct property type
				TypeMirror propType = methodElement.getParameters().get(0).asType();
				Property property = properties.get(propName);
				if (property == null) {
					property = new Property();
					property.name = propName;
					property.propType = propType;
					properties.put(propName, property);
				} else if (property.setter == null && typeUtils.isSameType(property.propType, propType)) {
					++fullProperties;
				} else {
					return null;
				}
				property.setter = methodElement;
			} else if ((propName = getPropetyName(name, "with")) != null) {
				// Chain setter should have a single argument and the parent class as return type
				if (methodElement.getParameters().size() != 1
						|| !typeUtils.isSameType(methodElement.getReturnType(), intfType)) {
					return null;
				}
				// Setters should have a single argument with correct property type
				TypeMirror propType = methodElement.getParameters().get(0).asType();
				Property property = properties.get(propName);
				if (property == null) {
					property = new Property();
					property.name = propName;
					property.propType = propType;
					properties.put(propName, property);
				} else if (property.chainSetter != null || !typeUtils.isSameType(property.propType, propType)) {
					return null;
				}
				property.chainSetter = methodElement;
			} else {
				return null;
			}
		}
		// If there are any partial properties, fail
		if (fullProperties != properties.size()) {
			return null;
		}
		// Result
		ImplClassInfo info = new ImplClassInfo();
		info.intfName = intfElement.getQualifiedName().toString();
		info.clsName = intfElement.getSimpleName() + "_impl";
		info.properties = properties.values();
		return info;
	}

	/**
	 * Generate the results of this processing run.
	 */
	private boolean generateResults() throws IOException {
		if (generatedClasses.isEmpty()) {
			return false;
		}
		// Generate the container class
		Map<String, Object> params = new HashMap<>();
		params.put("pkgName", targetPackage);
		params.put("containerClassName", targetClass);
		params.put("classes", generatedClasses);
		// Write the target class file
		JavaFileObject targetClassObj = processingEnv.getFiler().createSourceFile(targetPackage + "." + targetClass);
		try (OutputStream output = targetClassObj.openOutputStream()) {
			OutputStreamWriter writer = new OutputStreamWriter(output);
			Mustache template = getTemplate();
			template.execute(writer, params);
			writer.flush();
		}
		// Add the providers
		FileObject res1 = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
				"META-INF/services/" + SERVICE_CLASS.getName());
		try (OutputStream output = res1.openOutputStream()) {
			PrintStream ps = new PrintStream(output);
			ps.println(targetPackage + "." + targetClass + "$ProviderService");
			ps.flush();
		}
		return false;
	}

	private Mustache getTemplate() {
		if (template != null) {
			return template;
		}
		InputStream istm = YadgenProcessor.class.getResourceAsStream("/template.txt");
		InputStreamReader reader = new InputStreamReader(istm);
		MustacheFactory factory = new DefaultMustacheFactory();
		return template = factory.compile(reader, "template1");
	}

	private static String getPropetyName(String name, String prefix) {
		final char firstChar = name.charAt(prefix.length());
		if (name.length() > prefix.length() && name.startsWith(prefix) && Character.isUpperCase(firstChar)) {
			return Character.toLowerCase(firstChar) + name.substring(prefix.length() + 1);
		}
		return null;
	}

	public static class ImplClassInfo {
		public String intfName;
		public String clsName;
		public Collection<Property> properties;
	}

	public static class Property {
		public String name;
		public ExecutableElement getter;
		public ExecutableElement setter;
		public ExecutableElement chainSetter;
		public TypeMirror propType;

		public String getFieldName() {
			return "val_" + name;
		}

		public boolean isPrimitive() {
			return getter.getReturnType().getKind().isPrimitive();
		}

		public boolean isArray() {
			return getter.getReturnType().getKind() == TypeKind.ARRAY;
		}
	}

}

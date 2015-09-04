package org.nibeans.processor;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.nibeans.IBean;
import org.nibeans.internal.BeanProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

/**
 * Processes classes annotated with {@link org.nibeans.IBean} and generated default implementations for them.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
@SupportedOptions({ NIBeansProcessor.OPT_SOURCE_PACKAGES, NIBeansProcessor.OPT_TARGET_CLASS })
public class NIBeansProcessor extends AbstractProcessor {
	private static final Logger LOG = LoggerFactory.getLogger(NIBeansProcessor.class);

	public static final String OPT_SOURCE_PACKAGES = "srcpackages";
	public static final String OPT_TARGET_CLASS = "tgtclass";

	private static final Class<? extends Annotation> BEAN_CLASS = IBean.class;
	private static final Class<?> SERVICE_CLASS = BeanProviderService.class;
	private static final Pattern NAME_PATTERN = Pattern.compile("^(\\w+(\\.\\w+)*)\\.(\\w+)$");

	private final Set<String> packagesToScan = new HashSet<>();
	private String targetPackage;
	private String targetClass;
	private Mustache template;
	private final Map<TypeElement, ImplClassInfo> processedInterfaces = new HashMap<>();

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
					processedInterfaces.put(intfElement, clsInfo);
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
		final Types typeUtils = processingEnv.getTypeUtils();
		// Only a singly base interface is supported, it should also be an IBean interface
		// Generic parameters are not supported
		if (intfElement.getInterfaces().size() > 1 || !intfElement.getTypeParameters().isEmpty()) {
			return null;
		}
		TypeElement baseInterface = null;
		if (intfElement.getInterfaces().size() == 1) {
			TypeMirror baseInterfaceType = intfElement.getInterfaces().get(0);
			if (baseInterfaceType.getKind() != TypeKind.DECLARED) {
				return null;
			}
			Element baseInterfaceElm = ((DeclaredType) baseInterfaceType).asElement();
			if (baseInterfaceElm.getKind() != ElementKind.INTERFACE) {
				return null;
			}
			baseInterface = (TypeElement) baseInterfaceElm;
		}
		// Working descriptor
		ImplClassInfo info = new ImplClassInfo(intfElement, baseInterface);
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
				if (!processGetter(propName, methodElement, info)) {
					return null;
				}
			} else if ((propName = getPropetyName(name, "is")) != null) {
				// isSomething() must be a boolean getter
				TypeMirror propType = methodElement.getReturnType();
				PrimitiveType boolType = typeUtils.getPrimitiveType(TypeKind.BOOLEAN);
				if (!(typeUtils.isSameType(boolType, propType)
						|| typeUtils.isSameType(boolType, typeUtils.unboxedType(propType)))
						|| !processGetter(propName, methodElement, info)) {
					return null;
				}
			} else if ((propName = getPropetyName(name, "set")) != null) {
				if (!processSetter(propName, methodElement, info)) {
					return null;
				}
			} else if ((propName = getPropetyName(name, "with")) != null) {
				if (!processChainSetter(propName, methodElement, info)) {
					return null;
				}
			} else {
				return null;
			}
		}
		// If there are any partial properties, fail
		if (info.fullProperties < info.properties.size()) {
			return null;
		}
		// Result
		return info;
	}

	private boolean processGetter(String propName, ExecutableElement methodElement, ImplClassInfo info) {
		// Getters should have no arguments
		if (!methodElement.getParameters().isEmpty()) {
			return false;
		}
		// Match return type
		TypeMirror propType = methodElement.getReturnType();
		Property property = info.properties.get(propName);
		if (property == null) {
			property = addProperty(propName, propType, info);
		} else if (property.getter != null) {
			return false;
		} else if (property.getter == null && processingEnv.getTypeUtils().isSameType(property.fieldType, propType)) {
			++info.fullProperties;
		} else {
			return false;
		}
		property.getter = methodElement;
		return true;
	}

	private boolean processSetter(String propName, ExecutableElement methodElement, ImplClassInfo info) {
		// Setters should have no return values and a single argument
		if (methodElement.getReturnType().getKind() != TypeKind.VOID || methodElement.getParameters().size() != 1) {
			return false;
		}
		// Setters should have a single argument with correct property type
		TypeMirror setterType = methodElement.getParameters().get(0).asType();
		Property property = info.properties.get(propName);
		if (property == null) {
			property = addProperty(propName, setterType, info);
		} else if (property.setter == null && isSameType(property.fieldType, setterType)) {
			++info.fullProperties;
			boxFieldTypeIfNecessary(setterType, property);
		} else {
			return false;
		}
		property.setter = methodElement;
		property.setterType = setterType;
		return true;
	}

	private boolean processChainSetter(String propName, ExecutableElement methodElement, ImplClassInfo info) {
		Types typeUtils = processingEnv.getTypeUtils();
		DeclaredType intfType = typeUtils.getDeclaredType(info.intfElement);
		// Chain setter should have a single argument and the parent class as return type
		if (methodElement.getParameters().size() != 1
				|| !typeUtils.isSameType(methodElement.getReturnType(), intfType)) {
			return false;
		}
		// Setters should have a single argument with correct property type
		TypeMirror chainSetterType = methodElement.getParameters().get(0).asType();
		Property property = info.properties.get(propName);
		if (property == null) {
			property = addProperty(propName, chainSetterType, info);
		} else if (property.chainSetter != null || !isSameType(property.fieldType, chainSetterType)) {
			return false;
		}
		boxFieldTypeIfNecessary(chainSetterType, property);
		property.chainSetter = methodElement;
		property.chainSetterType = chainSetterType;
		return true;
	}

	/**
	 * Box the field type if it is primitive and the setter might give it a boxed value
	 */
	private void boxFieldTypeIfNecessary(TypeMirror setterType, Property property) {
		if (property.fieldType.getKind().isPrimitive() && !setterType.getKind().isPrimitive()) {
			property.fieldType = setterType;
		}
	}

	private boolean isSameType(TypeMirror t1, TypeMirror t2) {
		final Types typeUtils = processingEnv.getTypeUtils();
		if (typeUtils.isSameType(t1, t2)) {
			return true;
		}
		final PrimitiveType pt1 = getPrimitiveType(t1);
		if (pt1 == null) {
			return false;
		}
		final PrimitiveType pt2 = getPrimitiveType(t2);
		return pt1.equals(pt2);
	}

	private PrimitiveType getPrimitiveType(TypeMirror t) {
		if (t.getKind().isPrimitive()) {
			return (PrimitiveType) t;
		}
		try {
			return processingEnv.getTypeUtils().unboxedType(t);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private Property addProperty(String propName, TypeMirror propType, ImplClassInfo info) {
		Property property;
		property = new Property();
		property.name = propName;
		property.fieldType = propType;
		info.properties.put(propName, property);
		return property;
	}

	/**
	 * Generate the results of this processing run.
	 */
	private boolean generateResults() throws IOException {
		if (processedInterfaces.isEmpty()) {
			return false;
		}
		// Link base class implementations
		List<ImplClassInfo> validImpls = new ArrayList<>(processedInterfaces.size());
		for (ImplClassInfo implClassInfo : processedInterfaces.values()) {
			if (validateImplClassInfo(implClassInfo)) {
				validImpls.add(implClassInfo);
			}
		}
		// Sort to keep the output consistent
		Collections.sort(validImpls, new Comparator<ImplClassInfo>() {
			@Override
			public int compare(ImplClassInfo o1, ImplClassInfo o2) {
				String qn1 = o1.intfElement.getQualifiedName().toString();
				String qn2 = o2.intfElement.getQualifiedName().toString();
				return qn1.compareTo(qn2);
			}
		});
		// Generate the container class
		Map<String, Object> params = new HashMap<>();
		params.put("pkgName", targetPackage);
		params.put("containerClassName", targetClass);
		params.put("classes", validImpls);
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

	private boolean validateImplClassInfo(ImplClassInfo implClassInfo) {
		if (implClassInfo.invalid) {
			return false;
		}
		if (implClassInfo.baseInterface == null || implClassInfo.baseImpl != null) {
			return true;
		}
		// Was the base interface actually processed?
		ImplClassInfo baseImpl = processedInterfaces.get(implClassInfo.baseInterface);
		if (baseImpl == null || !validateImplClassInfo(baseImpl)) {
			implClassInfo.invalid = true;
			return false;
		}
		// Done
		implClassInfo.baseImpl = baseImpl;
		return true;
	}

	private Mustache getTemplate() {
		if (template != null) {
			return template;
		}
		InputStream istm = NIBeansProcessor.class.getResourceAsStream("/template.txt");
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
		public final TypeElement intfElement;
		public final TypeElement baseInterface;
		public ImplClassInfo baseImpl;
		public final String clsName;
		public Map<String, Property> properties = new TreeMap<>();
		public final Collection<Property> propertyDefs = properties.values();
		public int fullProperties = 0;
		boolean invalid = false;

		ImplClassInfo(TypeElement intfElement, TypeElement baseInterface) {
			this.intfElement = intfElement;
			this.baseInterface = baseInterface;
			clsName = intfElement.getSimpleName() + "_impl";
		}
	}

	public static class Property {
		public String name;
		public ExecutableElement getter;
		public ExecutableElement setter;
		public TypeMirror setterType;
		public ExecutableElement chainSetter;
		public TypeMirror chainSetterType;
		public TypeMirror fieldType;

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

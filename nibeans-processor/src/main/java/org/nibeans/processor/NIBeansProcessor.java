/**
 * Copyright (C) 2015 opendevz (opendevz@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nibeans.processor;

import java.io.IOException;
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

import org.nibeans.NIBean;

/**
 * Processes classes annotated with {@link org.nibeans.NIBean} and generated default implementations for them.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
@SupportedOptions({ //
		NIBeansProcessor.OPT_SOURCE_PACKAGES, //
		NIBeansProcessor.OPT_TARGET_CLASS, //
		NIBeansProcessor.OPT_STRICT })
public class NIBeansProcessor extends AbstractProcessor {

	private static final String OPTIONS_PREFIX = "nib.";
	public static final String OPT_SOURCE_PACKAGES = OPTIONS_PREFIX + "srcpackages";
	public static final String OPT_TARGET_CLASS = OPTIONS_PREFIX + "tgtclass";
	public static final String OPT_STRICT = OPTIONS_PREFIX + "strict";

	private static final String DEFAULT_TARGET_PACKAGE = ".beanimplementations";
	private static final String DEFAULT_TARGET_CLASS = "BeanImplementations";
	private static final Class<? extends Annotation> BEAN_CLASS = NIBean.class;
	private static final Pattern NAME_PATTERN = Pattern.compile("^(\\w+(\\.\\w+)*)\\.(\\w+)$");

	// Processor options
	private final Set<String> packagesToScan = new HashSet<>();
	private String targetPackage;
	private String targetClass;
	private boolean isStrict;

	// Working objects
	private final Map<TypeElement, ImplClassInfo> processedInterfaces = new HashMap<>();
	private IssueTracker tracker;
	private Generator generator;

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
			if (targetClass == null) {
				throw new IllegalArgumentException("Missing or bad value for option -A" + OPT_TARGET_CLASS);
			}
		} else {
			// If no target implementation container class is specified, use the default one
			targetPackage = packagesToScan.iterator().next() + DEFAULT_TARGET_PACKAGE;
			targetClass = DEFAULT_TARGET_CLASS;
		}
		// Strict mode
		String strictValue = processingEnv.getOptions().get(OPT_STRICT);
		if (strictValue != null) {
			isStrict = Boolean.valueOf(strictValue);
		}
		// Other
		tracker = new IssueTracker(processingEnv.getMessager(), isStrict);
		generator = new Generator(processingEnv.getFiler());
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Collections.singleton(BEAN_CLASS.getName());
	}

	/** {@inheritDoc} */
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
				// Make sure the interface is inside one of the packages explicitly specified
				Element packageElm = element.getEnclosingElement();
				if (packageElm == null || packageElm.getKind() != ElementKind.PACKAGE
						|| !packagesToScan.contains(((PackageElement) packageElm).getQualifiedName().toString())) {
					continue;
				}
				// Process this interface
				tracker.enterScope(element);
				ImplClassInfo clsInfo = processInterafce(element);
				if (clsInfo != null) {
					processedInterfaces.put(clsInfo.intfElement, clsInfo);
					claimed = true;
				}
				tracker.leaveScope();
			}
			return claimed;
		} catch (IOException e) {
			tracker.addIssue(e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Process a single bean interface.
	 */
	private ImplClassInfo processInterafce(Element element) throws IOException {
		// Only interfaces are allowed
		if (element.getKind() != ElementKind.INTERFACE) {
			tracker.addIssue("not an interface type");
			return null;
		}
		TypeElement intfElement = (TypeElement) element;
		// Only a singly base interface is supported, it should also be an IBean interface
		// Generic parameters are not supported
		if (intfElement.getInterfaces().size() > 1) {
			tracker.addIssue("there is more than one base interface");
			return null;
		}
		if (!intfElement.getTypeParameters().isEmpty()) {
			tracker.addIssue("there are generic type arguments");
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
		boolean good = true;
		for (Element enclosedElement : intfElement.getEnclosedElements()) {
			if (enclosedElement.getKind() != ElementKind.METHOD) {
				continue;
			}
			ExecutableElement methodElement = (ExecutableElement) enclosedElement;
			final String name = methodElement.getSimpleName().toString();
			// Check the name
			tracker.enterScope(methodElement);
			String propName;
			boolean methodIsGood = false;
			if (!methodElement.getTypeParameters().isEmpty()) {
				tracker.addIssue("there are member-level generic type arguments", methodElement);
				methodIsGood = false;
			} else if ((propName = getPropetyName(name, "get")) != null) {
				methodIsGood = processGetter(propName, methodElement, info);
			} else if ((propName = getPropetyName(name, "is")) != null) {
				methodIsGood = processIsGetter(propName, methodElement, info);
			} else if ((propName = getPropetyName(name, "set")) != null) {
				methodIsGood = processSetter(propName, methodElement, info);
			} else if ((propName = getPropetyName(name, "with")) != null) {
				methodIsGood = processChainSetter(propName, methodElement, info);
			} else {
				tracker.addIssue("unsupported method %s", methodElement);
				methodIsGood = false;
			}
			tracker.leaveScope();
			good = methodIsGood && good;
		}
		// If there are any partial properties, fail
		for (Property property : info.properties.values()) {
			if (property.getter == null && property.booleanGetter == null) {
				tracker.addIssue("property %s has no getter", property.name);
				good = false;
			} else if (property.setter == null && property.chainSetter == null) {
				tracker.addIssue("property %s has no setter", property.name);
				good = false;
			}
		}
		// Result
		return good ? info : null;
	}

	private boolean processGetter(String propName, ExecutableElement methodElement, ImplClassInfo info) {
		Property property = processCommonGetter(propName, methodElement, info);
		if (property != null) {
			property.getter = methodElement;
			return true;
		}
		return false;
	}

	private boolean processIsGetter(String propName, ExecutableElement methodElement, ImplClassInfo info) {
		final Types typeUtils = processingEnv.getTypeUtils();
		// isSomething() must be a boolean getter
		TypeMirror propTypePrim = getPrimitiveType(methodElement.getReturnType());
		PrimitiveType boolType = typeUtils.getPrimitiveType(TypeKind.BOOLEAN);
		if (propTypePrim == null || !(typeUtils.isSameType(boolType, propTypePrim))) {
			tracker.addIssue("non-boolean return type %s", methodElement.getReturnType());
			return false;
		}
		Property property = processCommonGetter(propName, methodElement, info);
		if (property != null) {
			property.booleanGetter = methodElement;
			return true;
		}
		return false;
	}

	private Property processCommonGetter(String propName, ExecutableElement methodElement, ImplClassInfo info) {
		// Getters should have no arguments
		if (!methodElement.getParameters().isEmpty()) {
			tracker.addIssue("unsupported getter signature");
			return null;
		}
		// Match return type
		TypeMirror propType = methodElement.getReturnType();
		Property property = info.properties.get(propName);
		if (property == null) {
			property = addProperty(propName, propType, info);
		} else if (!isSameType(property.fieldType, propType)) {
			tracker.addIssue("getter type %s isn't compatible with %s", methodElement, propType, property.fieldType);
			return null;
		}
		return property;
	}

	private boolean processSetter(String propName, ExecutableElement methodElement, ImplClassInfo info) {
		// Setters should have no return values and a single argument
		boolean badSig;
		boolean setterReturnsObject = false;
		if (methodElement.getParameters().size() != 1) {
			badSig = true;
		} else if (methodElement.getReturnType().getKind() != TypeKind.VOID) {
			// A setter can return either void or the enclosing interface
			DeclaredType enclosingType = processingEnv.getTypeUtils().getDeclaredType(info.intfElement);
			if (processingEnv.getTypeUtils().isSameType(enclosingType, methodElement.getReturnType())) {
				setterReturnsObject = true;
				badSig = false;
			} else {
				badSig = true;
			}
		} else {
			badSig = false;
		}
		if (badSig) {
			tracker.addIssue("unsupported setter signature");
			return false;
		}
		// Setters should have a single argument with correct property type
		TypeMirror setterType = methodElement.getParameters().get(0).asType();
		Property property = info.properties.get(propName);
		if (property == null) {
			property = addProperty(propName, setterType, info);
		} else if (property.setter != null) {
			tracker.addIssue("conflict with already defined setter %s", property.setter);
			return false;
		} else if (isSameType(property.fieldType, setterType)) {
			boxFieldTypeIfNecessary(setterType, property);
		} else {
			tracker.addIssue("setter type %s isn't compatible with %s", setterType, property.fieldType);
			return false;
		}
		property.setter = methodElement;
		property.setterType = setterType;
		property.setterReturnsObject = setterReturnsObject;
		return true;
	}

	private boolean processChainSetter(String propName, ExecutableElement methodElement, ImplClassInfo info) {
		Types typeUtils = processingEnv.getTypeUtils();
		DeclaredType intfType = typeUtils.getDeclaredType(info.intfElement);
		// Chain setter should have a single argument and the parent class as return type
		if (methodElement.getParameters().size() != 1
				|| !typeUtils.isSameType(methodElement.getReturnType(), intfType)) {
			tracker.addIssue("unsupported chain setter signature");
			return false;
		}
		// Setters should have a single argument with correct property type
		TypeMirror chainSetterType = methodElement.getParameters().get(0).asType();
		Property property = info.properties.get(propName);
		if (property == null) {
			property = addProperty(propName, chainSetterType, info);
		} else if (property.chainSetter != null) {
			tracker.addIssue("conflict with already defined chain getter %s", property.getter);
			return false;
		} else if (!isSameType(property.fieldType, chainSetterType)) {
			tracker.addIssue("chain setter type %s isn't compatible with %s", chainSetterType, property.fieldType);
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
		// Link base class implementations
		List<ImplClassInfo> validImpls = new ArrayList<>(processedInterfaces.size());
		for (ImplClassInfo implClassInfo : processedInterfaces.values()) {
			tracker.enterScope(implClassInfo.intfElement);
			if (validateImplClassInfo(implClassInfo)) {
				validImpls.add(implClassInfo);
			}
			tracker.leaveScope();
		}
		// Print any issues
		tracker.printIssues();
		// Nothing to generate?
		if (validImpls.isEmpty()) {
			return false;
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
		// Generate the results
		generator.generate(targetPackage, targetClass, validImpls);
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
			tracker.addIssue("base interface %s is not a generated bean", implClassInfo.baseInterface);
			return false;
		}
		// Done
		implClassInfo.baseImpl = baseImpl;
		return true;
	}

	private static String getPropetyName(String name, String prefix) {
		if (name.length() > prefix.length() && name.startsWith(prefix)) {
			final char firstChar = name.charAt(prefix.length());
			if (Character.isUpperCase(firstChar)) {
				return Character.toLowerCase(firstChar) + name.substring(prefix.length() + 1);
			}
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
		public ExecutableElement booleanGetter;
		public ExecutableElement setter;
		public TypeMirror setterType;
		public boolean setterReturnsObject;
		public ExecutableElement chainSetter;
		public TypeMirror chainSetterType;
		public TypeMirror fieldType;

		public String getFieldName() {
			return name;
		}

		public boolean isPrimitive() {
			return getter.getReturnType().getKind().isPrimitive();
		}

		public boolean isArray() {
			return getter.getReturnType().getKind() == TypeKind.ARRAY;
		}
	}

}

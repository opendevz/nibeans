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
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.nibeans.internal.BeanProviderService;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

/**
 * Generate the source and resource files that result from the annotation processing.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
public class Generator {

	private final Filer filer;
	private STGroup templateGroup;

	public Generator(Filer filer) {
		this.filer = filer;
	}

	public void generate(String packageName, String containerClassName, Collection<?> implementationClasses)
			throws IOException {
		// Generate the container class
		ST tmpl = getTemplate();
		tmpl.add("pkgName", packageName);
		tmpl.add("containerClassName", containerClassName);
		tmpl.add("classes", implementationClasses);
		// Write the target class file
		JavaFileObject targetClassObj = filer.createSourceFile(packageName + "." + containerClassName);
		try (OutputStream output = targetClassObj.openOutputStream()) {
			String contents = tmpl.render();
			byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
			output.write(bytes);
		}
		// Add the providers
		FileObject res1 = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
				"META-INF/services/" + BeanProviderService.class.getName());
		try (OutputStream output = res1.openOutputStream()) {
			PrintStream ps = new PrintStream(output);
			ps.print(packageName);
			ps.print('.');
			ps.print(containerClassName);
			ps.println("$ProviderService");
			ps.flush();
		}
	}

	private ST getTemplate() {
		if (templateGroup == null) {
			URL in = getClass().getResource("/template.txt");
			templateGroup = new STGroupFile(in, StandardCharsets.UTF_8.name(), '<', '>');
			templateGroup.load();
		}
		return templateGroup.getInstanceOf("impl_file");
	}

}

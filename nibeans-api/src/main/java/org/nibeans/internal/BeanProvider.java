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
package org.nibeans.internal;

/**
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 * @param <T>
 *            An interface that defines a java bean, annotated with {@link org.nibeans.NIBean} by definition.
 */
public interface BeanProvider<T> {

	/**
	 * Gets the class definition of the bean interface.
	 */
	public Class<T> getBeanInterface();

	/**
	 * Create new instance of a class that implements the bean interface defined by
	 * {@link #getBeanInterface()}
	 */
	public T createInstance();

}

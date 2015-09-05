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
package org.nibeans;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines a simple bean interface for which a default implementation can be generated.
 * <p/>
 * 
 * A default implementation is generated if the bean has matching property getters and setters with exactly
 * the same type, and no other methods. Ordering of methods is insignificant. Example:
 * 
 * <pre>
 * &#64;IBean
 * public interface Person {
 * 
 * 	String getName();
 * 
 * 	void setName(String v);
 * 
 * 	List&lt;Person&gt; getChildren();
 * 
 * 	void setChildren(List&lt;Person&gt; v);
 * }
 * </pre>
 * 
 * If the interface has any non-conforming methods, it is ignored by the generator.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface IBean {
}

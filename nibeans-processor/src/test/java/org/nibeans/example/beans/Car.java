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
package org.nibeans.example.beans;

import org.nibeans.NIBean;

@NIBean
public interface Car {

	String getMake();

	void setMake(String v);

	Car withMake(String v);

	boolean isAutomatic();

	boolean getAutomatic();

	void setAutomatic(Boolean v);

	String getPlateID();

	Car setPlateID(String v);

	String[] getOwners();

	Car withOwners(String[] v);

}

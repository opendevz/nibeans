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
package org.nibeans.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.nibeans.BeanFactory;
import org.nibeans.example.beans.BadBeanA;
import org.nibeans.example.beans.Car;
import org.nibeans.example.beans.GasolineCar;
import org.nibeans.example.beans.GenericBeanA;
import org.nibeans.example.beans.Person;

public class BeansTest {

	private static final String NAME = "philip";
	private static final int AGE = 30;

	@Test
	public void testGetterSetter() {
		Person person = createPersonBean();
		assertEquals(NAME, person.getName());
		assertEquals(AGE, person.getAge());
	}

	@Test
	public void testEquals() {
		Person person = createPersonBean();
		assertTrue(person.equals(new Person() {

			@Override
			public void setName(String v) {
			}

			@Override
			public void setAge(int v) {
			}

			@Override
			public String getName() {
				return NAME;
			}

			@Override
			public int getAge() {
				return AGE;
			}
		}));
	}

	@Test
	public void testBooleanProperties() {
		Car car = BeanFactory.getInstance().createBean(Car.class);
		car.setAutomatic(true);
		assertTrue(car.isAutomatic());
		// This makes sure Boolean is kept in the setter
		car.setAutomatic(null);
	}

	@Test
	public void testInheritance() {
		Car car = BeanFactory.getInstance().createBean(Car.class);
		GasolineCar gasCar1 = BeanFactory.getInstance().createBean(GasolineCar.class);
		// Make sure the implementation inherits that of the base interface
		assertNotEquals(car.getClass(), gasCar1.getClass());
		assertTrue(car.getClass().isAssignableFrom(gasCar1.getClass()));
		// Not equal
		car.setAutomatic(true);
		car.setMake("Audi");
		gasCar1.setAutomatic(true);
		gasCar1.setMake("Audi");
		gasCar1.setOctaneLevel(95);
		assertTrue(car.equals(gasCar1));
		assertFalse(gasCar1.equals(car));
		// Compare two cars
		GasolineCar gasCar2 = BeanFactory.getInstance().createBean(GasolineCar.class);
		gasCar2.setAutomatic(true);
		gasCar2.setMake("BMW");
		gasCar2.setOctaneLevel(95);
		assertFalse(gasCar1.equals(gasCar2));
	}

	@Test
	public void testSettersThatReturnObject() {
		Car car = BeanFactory.getInstance().createBean(Car.class);
		// Normal setters
		final String plateId = "aabbcc";
		Car returnedObj = car.setPlateID(plateId);
		assertEquals(plateId, car.getPlateID());
		assertTrue(returnedObj == car);
		// Chain setters
		final String make = "BMW";
		returnedObj = car.withMake(make);
		assertEquals(make, car.getMake());
		assertTrue(returnedObj == car);
	}

	@Test
	public void testTypeLevelGenericArgs() {
		assertNull(BeanFactory.getInstance().createBean(GenericBeanA.class));
	}

	@Test
	public void testMethodLevelGenericArgs() {
		assertNull(BeanFactory.getInstance().createBean(GenericBeanA.class));
	}

	@Test
	public void testTooShortMethodNames() {
		assertNull(BeanFactory.getInstance().createBean(BadBeanA.class));
	}

	private static Person createPersonBean() {
		Person person = BeanFactory.getInstance().createBean(Person.class);
		person.setName(NAME);
		person.setAge(AGE);
		return person;
	}

}

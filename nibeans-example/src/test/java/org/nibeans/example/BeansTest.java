package org.nibeans.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.nibeans.BeanFactory;

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
		Car car = BeanFactory.createBean(Car.class);
		car.setAutomatic(true);
		assertTrue(car.isAutomatic());
		// This makes sure Boolean is kept in the setter
		car.setAutomatic(null);
	}

	@Test
	public void testInheritance() {
		Car car = BeanFactory.createBean(Car.class);
		GasolineCar gasCar1 = BeanFactory.createBean(GasolineCar.class);
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
		GasolineCar gasCar2 = BeanFactory.createBean(GasolineCar.class);
		gasCar2.setAutomatic(true);
		gasCar2.setMake("BMW");
		gasCar2.setOctaneLevel(95);
		assertFalse(gasCar1.equals(gasCar2));
	}

	private static Person createPersonBean() {
		Person person = BeanFactory.createBean(Person.class);
		person.setName(NAME);
		person.setAge(AGE);
		return person;
	}

}

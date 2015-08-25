package org.yadgen.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.yadgen.BeanFactory;

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

	private static Person createPersonBean() {
		Person person = BeanFactory.createBean(Person.class);
		person.setName(NAME);
		person.setAge(AGE);
		return person;
	}

}

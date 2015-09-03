package org.nibeans.example;

import org.nibeans.IBean;

@IBean
public interface Person {

	int getAge();

	void setAge(int v);

	String getName();

	void setName(String v);

}

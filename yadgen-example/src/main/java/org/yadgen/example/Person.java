package org.yadgen.example;

import org.yadgen.IBean;

@IBean
public interface Person {

	int getAge();

	void setAge(int v);

	String getName();

	void setName(String v);

}

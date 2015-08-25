package org.yadgen.example;

import org.yadgen.IBean;

@IBean
public interface Car {

	String getMake();

	void setMake(String v);

	Car withMake(String v);

	boolean isAutomatic();

	void setAutomatic(Boolean v);

}

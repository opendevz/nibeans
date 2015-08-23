package org.yadgen.example;

import org.yadgen.IBean;

@IBean
public interface Car {

	String getMake();

	void setMake(String v);

	Car withMake(String v);

	Boolean isAutomatic();

	void setAutomatic(boolean v);

}

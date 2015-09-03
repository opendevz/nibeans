package org.nibeans.example;

import org.nibeans.IBean;

@IBean
public interface Car {

	String getMake();

	void setMake(String v);

	Car withMake(String v);

	boolean isAutomatic();

	void setAutomatic(Boolean v);

}

package org.nibeans.example;

import org.nibeans.IBean;

@IBean
public interface GasolineCar extends Car {

	int getOctaneLevel();

	void setOctaneLevel(int v);

}

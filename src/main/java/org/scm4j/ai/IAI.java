package org.scm4j.ai;

public interface IAI {

	void install(String productCoords);

	void unIninstall(String productCoors);

	void upgrade(String newProductCoords);

}

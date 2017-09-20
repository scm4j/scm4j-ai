package org.scm4j.ai.api;

public interface IAI {

	void install(String productCoords);

	void uninstall(String productCoors);

	void upgrade(String newProductCoords);

}

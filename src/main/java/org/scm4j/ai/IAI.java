package org.scm4j.ai;

public interface IAI {
	public void install(String productCoords);

	public void unIninstall(String productCoors);

	public void upgrade(String newProductCoords);
}

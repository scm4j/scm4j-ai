package org.scm4j.ai.installers;

import java.io.File;

public class ExeRunner implements IInstaller {
	
	private File product;

	public ExeRunner(File product) {
		this.product = product;
	}

	@Override
	public void install() {
		try {
			Process p = Runtime.getRuntime().exec(product.getPath());
			p.waitFor();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

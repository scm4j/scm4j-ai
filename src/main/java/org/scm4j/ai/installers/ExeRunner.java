package org.scm4j.ai.installers;


import org.scm4j.ai.api.IDeployer;

import java.io.File;

public class ExeRunner implements IDeployer {
	
	private File product;

	public ExeRunner(File product) {
		this.product = product;
	}

	@Override
	public void deploy() {
		try {
			Process p = Runtime.getRuntime().exec(product.getPath());
			p.waitFor();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void unDeploy() {

	}

	@Override
	public boolean canDeploy() {
		return false;
	}

	@Override
	public boolean checkIntegrity() {
		return false;
	}
}

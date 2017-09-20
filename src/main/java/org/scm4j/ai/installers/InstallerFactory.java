package org.scm4j.ai.installers;

import org.scm4j.ai.api.IDeployer;

import java.io.File;

public class InstallerFactory {
	
	public IDeployer getInstaller(File product) {
		return new ExeRunner(product);
	}

}

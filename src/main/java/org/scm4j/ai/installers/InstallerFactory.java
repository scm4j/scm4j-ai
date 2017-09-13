package org.scm4j.ai.installers;

import org.scm4j.ai.api.IInstaller;

import java.io.File;

public class InstallerFactory {
	
	public IInstaller getInstaller(File product) {
		return new ExeRunner(product);
	}

}

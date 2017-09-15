package org.scm4j.ai;

import org.scm4j.ai.api.IInstaller;
import org.scm4j.commons.Coords;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class AI implements IAI {

	private File workingFolder;
	private String productListArtifactoryUrl;

	public AI(File workingFolder, String productListArtifactoryUrl) {
		this.workingFolder = workingFolder;
		this.productListArtifactoryUrl = productListArtifactoryUrl;
	}

	@Override
	public void install(String productCoords) {
		AIRunner runner = new AIRunner(workingFolder, productListArtifactoryUrl, null, null);
		Coords coords = new Coords(productCoords);
		File jarFile = runner.get(coords.getGroupId(), coords.getArtifactId(), coords.getVersion().toString(),
				coords.getExtension());
		String installerClassName = Utils.getExportedClassName(jarFile);
		if (installerClassName == null) {
			throw new RuntimeException("Installer class name is not located within jar");
		}
		try {
			Class<?> installerClass = Class.forName(installerClassName);
			Constructor<?> constructor = installerClass.getConstructor();
			Object result = constructor.newInstance();
			IInstaller installer;
			if (result.getClass().isAssignableFrom(IInstaller.class)) {
				installer = (IInstaller) result;
			} else {
				throw new RuntimeException("Provided " + installerClassName + " does not implements IInstaller");
			}
			//

		} catch (ClassNotFoundException e) {
			throw new RuntimeException(installerClassName + " class not found");
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(installerClassName + " class has no constructor");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void unIninstall(String productCoors) {

	}

	@Override
	public void upgrade(String newProductCoords) {
	}
}

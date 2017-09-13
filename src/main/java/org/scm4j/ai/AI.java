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
		String installerClassName = getExportedClassName(jarFile);
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

	private String getExportedClassName(File jarFile) {
		try (JarFile jarfile = new JarFile(jarFile)) {

			// Get the manifest
			Manifest manifest = jarfile.getManifest();

			// Get the main attributes in the manifest
			Attributes attrs = manifest.getMainAttributes();
			for (Iterator<Object> it = attrs.keySet().iterator(); it.hasNext();) {
				// Get attribute name
				Attributes.Name attrName = (Attributes.Name) it.next();
				if (attrName.equals("Main-Class")) {
					return attrs.getValue(attrName);
				}
			}
			return null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

package org.scm4j.ai;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class AI implements IAI {

	private File workingFolder;

	public AI(File workingFolder) {
		this.workingFolder = workingFolder;
	}

	@Override
	public void install(String productCoords) {
		/**
		 * Download Product Artifact. It contains one deployer per product. All components are hardcoded within it. All versions are taken from resources
		 */
		AIRunner runner = new AIRunner(workingFolder, null, null, null);
		DepCoords coords = new DepCoords(productCoords);
		File jarFile = runner.get(coords.getGroupId(), coords.getArtifactId(), coords.getVersion().toString(),
				coords.getExtension());
		/**
		 * Take all classes from this jar
		 */
		String deployerClassName = getExportedClassName(jarFile);
		if (deployerClassName == null) {
			throw new RuntimeException("Deployer class name is not located within jar");
		}
		try {
			Class<?> deployerClass = Class.forName(deployerClassName);
			Constructor<?> constructor = deployerClass.getConstructor();
			Object result = constructor.newInstance();
			IDeployer deployer;
			if (result.getClass().isAssignableFrom(IDeployer.class)) {
				deployer = (IDeployer) result;
			} else {
				throw new RuntimeException("Provided " + deployerClassName + " does not implements IDeployer");
			}
			//

		} catch (ClassNotFoundException e) {
			throw new RuntimeException(deployerClassName + " class not found");
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(deployerClassName + " class has no constructor");
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
			Attributes attrs = (Attributes) manifest.getMainAttributes();
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

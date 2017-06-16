package org.scm4j.ai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.installers.InstallerFactory;

public class AIRunner {

	public static final String REPOSITORY_FOLDER_NAME = "repository";
	private List<ArtifactoryReader> repos = new ArrayList<>();
	private File repository;
	private InstallerFactory installerFactory;
	
	public void setInstallerFactory(InstallerFactory installerFactory) {
		this.installerFactory = installerFactory;
	}

	public AIRunner(File workingFolder) throws ENoConfig {
		repository = new File(workingFolder, REPOSITORY_FOLDER_NAME);
		try {
			if (!repository.exists()) {
				Files.createDirectory(repository.toPath());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		repos = ArtifactoryReader.loadFromWorkingFolder(workingFolder);
	}

	public List<ArtifactoryReader> getRepos() {
		return repos;
	}

	public List<String> listProducts() {
		Set<String> res = new HashSet<>();
		for (ArtifactoryReader repo : repos) {
			res.addAll(repo.getProducts());
		}
		return new ArrayList<>(res);
	}

	public List<String> listVersions(String productName) {
		Set<String> res = new HashSet<>();
		for (ArtifactoryReader repo : repos) {
			res.addAll(repo.getProductVersions(productName));
		}
		return new ArrayList<>(res);
	}
	
	public void install(File productDir) {
		installerFactory.getInstaller(productDir).install();
	}
	
	public File get(String productName, String version, String extension) throws EArtifactNotFound {
		File res = queryLocal(productName, version, extension);
		if (res == null) { 
			res = download(productName, version, extension);
			if (res == null) {
				throw new EArtifactNotFound(
						getArftifactStr(productName, version, extension) + " is not found in all known repositories");
			}
		} 
		return res;
	}
	
	public File queryLocal(String productName, String version, String extension) { 
		String fileRelativePath = Utils.getProductRelativePath(productName, version, extension);
		File res = new File(repository, fileRelativePath);
		if (!res.exists()) {
			return null;
		}
		return res;
	}

	private File download(String productName, String version, String extension) {
		File cachedArtifact = queryLocal(productName, version, extension);
		if (cachedArtifact != null) {
			return cachedArtifact;
		}
		
		String fileRelativePath = Utils.getProductRelativePath(productName, version, extension);
		File res = new File(repository, fileRelativePath);
		for (ArtifactoryReader repo : repos) {
			
			if (!repo.getProducts().contains(productName)) {
				continue;
			}
			
			if (!repo.getProductVersions(productName).contains(version)) {
				continue;
			}
			
			try {
				File parent = res.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}

				res.createNewFile();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			try (FileOutputStream out = new FileOutputStream(res);
				 InputStream in = getContent(repo.getProductUrl(productName, version, extension))) {
				IOUtils.copy(in, out);
			} catch (Exception e) {
				continue;
			}
			return res;
		}
		return null;
	}

	private String getArftifactStr(String productName, String version, String extension) {
		return productName + "-" + version + extension;
	}

	public InputStream getContent(String fileUrlSr) throws Exception {
		URL url = new URL(fileUrlSr);
		return url.openStream();
		
	}

}

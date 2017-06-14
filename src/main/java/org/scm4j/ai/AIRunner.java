package org.scm4j.ai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.installers.InstallerFactory;

public class AIRunner {

	private Map<String, Repository> repos = new HashMap<>();
	private File repository;
	private InstallerFactory installerFactory;
	
	public void setInstallerFactory(InstallerFactory installerFactory) {
		this.installerFactory = installerFactory;
	}

	public AIRunner(File workingFolder) throws ENoConfig {
		repository = new File(workingFolder, "repository");
		try {
			if (!repository.exists()) {
				Files.createDirectory(repository.toPath());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		List<Repository> repos = Repository.loadFromWorkingFolder(workingFolder);
		for (Repository repo : repos) {
			this.repos.put(repo.getUrl(), repo);
		}
	}

	public List<Repository> getRepos() {
		return new ArrayList<>(repos.values());
	}

	public List<String> listProducts() {
		Set<String> res = new HashSet<>();
		for (Repository repo : repos.values()) {
			res.addAll(repo.getProducts());
		}
		return new ArrayList<>(res);
	}

	public List<String> listVersions(String productName) {
		Set<String> res = new HashSet<>();
		for (Repository repo : repos.values()) {
			res.addAll(repo.getProductVersions(productName));
		}
		return new ArrayList<>(res);
	}
	
	public void install(File productDir) {
		installerFactory.getInstaller(productDir).install();
	}

	public File download(String productName, String version, String extension) {
		for (Repository repo : repos.values()) {
			if (!repo.getProducts().contains(productName)) {
				continue;
			}
			if (!repo.getProductVersions(productName).contains(version)) {
				continue;
			}
			String fileRelativeUrlStr = Utils.getProductRelativeUrl(productName, version, extension);

			File res = new File(repository, fileRelativeUrlStr);
			if (res.exists()) {
				return res;
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
		throw new EArtifactNotFound(
				getArftifactStr(productName, version, extension) + " is not found in all known repositories");
	}

	private String getArftifactStr(String productName, String version, String extension) {
		return productName + "-" + version + extension;
	}

	public InputStream getContent(String fileUrlSr) throws Exception {
		URL url = new URL(fileUrlSr);
		return url.openStream();
		
	}

}

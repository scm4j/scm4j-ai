package org.scm4j.ai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.exceptions.EProductNotFound;
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
		} catch (Exception e) {
			throw new RuntimeException (e);
		}
		
		setRepos(ArtifactoryReader.loadFromWorkingFolder(workingFolder));
	}

	public List<ArtifactoryReader> getRepos() {
		return repos;
	}

	public List<String> listProducts() {
		Set<String> res = new HashSet<>();
		try {
			for (ArtifactoryReader repo : getRepos()) {
				res.addAll(repo.getProducts().keySet());
			}
			return new ArrayList<>(res);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public List<String> listVersions(String groupId, String artifactId) {
		try {
			for (ArtifactoryReader repo : getRepos()) {
				if (repo.hasProduct(groupId, artifactId)) {
					return repo.getProductVersions(groupId, artifactId);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		throw new EProductNotFound();
	}

	public void install(File productDir) {
		installerFactory.getInstaller(productDir).install();
	}

	public File get(String groupId, String artifactId, String version, String extension) throws EArtifactNotFound {
		File res = queryLocal(groupId, artifactId, version, extension);
		if (res == null) {
			res = download(groupId, artifactId, version, extension);
			if (res == null) {
				throw new EArtifactNotFound(Utils.coordsToString(groupId, artifactId, version, extension)
						+ " is not found in all known repositories");
			}
		}
		return res;
	}

	public File queryLocal(String groupId, String artifactId, String version, String extension) {
		String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
		File res = new File(repository, fileRelativePath);
		if (!res.exists()) {
			return null;
		}
		return res;
	}

	private File download(String groupId, String artifactId, String version, String extension) {
		String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
		File res = new File(repository, fileRelativePath);
		for (ArtifactoryReader repo : getRepos()) {

			try {
				if (!repo.getProducts().keySet().contains(Utils.coordsToString(groupId, artifactId))) {
					continue;
				}
			} catch (Exception e) {
				continue;
			}

			try {
				if (!repo.getProductVersions(groupId, artifactId).contains(version)) {
					continue;
				}
			} catch (Exception e) {
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
				 InputStream in = repo.getContentStream(groupId, artifactId, version, extension)) {
				IOUtils.copy(in, out);
			} catch (Exception e) {
				continue;
			}
			return res;
		}
		return null;
	}
	
	public void setRepos(List<ArtifactoryReader> repos) {
		this.repos = repos;
	}

}

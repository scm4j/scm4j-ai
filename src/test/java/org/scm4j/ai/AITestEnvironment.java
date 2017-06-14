package org.scm4j.ai;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;

public class AITestEnvironment {
	
	private static final String TEST_ARTIFACTORIES_PATH = "org/scm4j/ai/RemoteArtifactories";
	
	private File baseTestFolder;
	private File envFolder;
	private File artifactoriesFolder;
	private File artifactory1Folder;
	private File artifactory2Folder;
	private String artifactory1Url;
	private String artifactory2Url;
	private File reposFile;
	private ClassLoader cl;
	
	public void prepareEnvironment() throws IOException {
		cl = Thread.currentThread().getContextClassLoader();
		File baseTestFolderFile = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test");
		FileUtils.deleteDirectory(baseTestFolderFile);
		baseTestFolder = Files.createDirectory(baseTestFolderFile.toPath()).toFile();
		createArtifactories();
		createEnvironment();
	}
	
	private void createArtifactories() throws IOException {
		artifactoriesFolder = Files
				.createDirectory(new File(baseTestFolder, "art").toPath())
				.toFile();
		artifactory1Folder = Files
				.createDirectory(new File(artifactoriesFolder, "RemoteArtifactory1").toPath())
				.toFile();
		artifactory2Folder = Files
				.createDirectory(new File(artifactoriesFolder, "RemoteArtifactory2").toPath())
				.toFile();
		artifactory1Url = "file:/" + artifactory1Folder.getPath().replace("\\", "/");
		artifactory2Url = "file:/" + artifactory2Folder.getPath().replace("\\", "/");
		FileUtils.copyDirectory(getResourceFolder(TEST_ARTIFACTORIES_PATH), artifactoriesFolder);
	}

	private void createEnvironment() throws IOException {
		envFolder = Files.createDirectory(new File(baseTestFolder, "env").toPath()).toFile();
		createReposFile();
	}

	private void createReposFile() throws IOException {
		reposFile = new File(envFolder, "repos");
		reposFile.createNewFile();
		FileUtils.writeLines(reposFile, Arrays.asList(artifactory1Url, artifactory2Url));
	}
	
	private File getResourceFolder(String path) throws IOException {
		final URL url = cl.getResource(path);
		return new File(url.getFile());
	}
	
	public File getBaseTestFolder() {
		return baseTestFolder;
	}

	public File getEnvFolder() {
		return envFolder;
	}

	public File getArtifactoriesFolder() {
		return artifactoriesFolder;
	}

	public File getArtifactory1Folder() {
		return artifactory1Folder;
	}

	public File getArtifactory2Folder() {
		return artifactory2Folder;
	}

	public String getArtifactory1Url() {
		return artifactory1Url;
	}

	public String getArtifactory2Url() {
		return artifactory2Url;
	}

	public File getReposFile() {
		return reposFile;
	}

}

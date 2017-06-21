package org.scm4j.ai;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;

public class AITestEnvironment {
	
	public static final String TEST_RESOURCES_PATH = "org/scm4j/ai/RemoteArtifactories";
	
	private File baseTestFolder;
	private File envFolder;
	private File artifactoriesFolder;
	private File artifactory1Folder;
	private File artifactory2Folder;
	private String artifactory1Url;
	private String artifactory2Url;
	private File productListsFile;
	
	public void prepareEnvironment() throws IOException {
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
		artifactory1Url = "file://localhost/" + artifactory1Folder.getPath().replace("\\", "/");
		artifactory2Url = "file://localhost/" + artifactory2Folder.getPath().replace("\\", "/");
//		FileUtils.copyDirectory(getResourceFolder(TEST_RESOURCES_PATH), artifactoriesFolder);
	}

	private void createEnvironment() throws IOException {
		envFolder = Files.createDirectory(new File(baseTestFolder, "env").toPath()).toFile();
		createReposFile();
	}

	private void createReposFile() throws IOException {
		productListsFile = new File(envFolder, ArtifactoryReader.PRODUCT_LISTS_FILE_NAME);
		productListsFile.createNewFile();
		//FileUtils.writeLines(reposFile, Arrays.asList(artifactory1Url, artifactory2Url));
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

	public File getProductListsFile() {
		return productListsFile;
	}

}

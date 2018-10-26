package org.scm4j.deployer.engine;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class AITestEnvironment {

	private File baseTestFolder;
	private File envFolder;
	private File artifactory1Folder;
	private File artifactory2Folder;
	private String artifactory1Url;
	private String artifactory2Url;

	public void prepareEnvironment() throws IOException {
		File baseTestFolderFile = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test");
		FileUtils.deleteDirectory(baseTestFolderFile);
		baseTestFolder = Files.createDirectory(baseTestFolderFile.toPath()).toFile();
		createArtifactories();
		writeReposInProductList(ArtifactoryWriter.PRODUCT_LIST_DEFAULT_VERSION);
		writeReposInProductList(ArtifactoryWriter.PRODUCT_LIST_VERSION);
	}

	private void createArtifactories() throws IOException {
		File artifactoriesFolder = Files
				.createDirectory(new File(baseTestFolder, "art").toPath())
				.toFile();
		artifactory1Folder = Files
				.createDirectory(new File(artifactoriesFolder, "RemoteArtifactory1").toPath())
				.toFile();
		artifactory2Folder = Files
				.createDirectory(new File(artifactoriesFolder, "RemoteArtifactory2").toPath())
				.toFile();
		artifactory1Url = artifactory1Folder.toURI().toURL().toString();
		artifactory2Url = artifactory2Folder.toURI().toURL().toString();
	}

	public void createEnvironment() throws IOException {
		envFolder = Files.createDirectory(new File(baseTestFolder, "env").toPath()).toFile();
	}

	@SuppressWarnings("unchecked")
	private void writeReposInProductList(String version) throws IOException {
		File productListFile = new File(artifactory1Folder, Utils.coordsToRelativeFilePath(ProductList.PRODUCT_LIST_GROUP_ID,
				ProductList.PRODUCT_LIST_ARTIFACT_ID, version, ".json", null));
		if (!productListFile.exists()) {
			productListFile.getParentFile().mkdirs();
			productListFile.createNewFile();
		}
		ProductListEntry productList = new ProductListEntry(new ArrayList<>(), new HashMap<>());
		List<String> repos = new ArrayList<>();
		repos.add(artifactory1Url);
		repos.add(artifactory2Url);
		productList.getRepositories().addAll(repos);
		Utils.writeJson(productList, productListFile);
	}

	public File getBaseTestFolder() {
		return baseTestFolder;
	}

	public File getEnvFolder() {
		return envFolder;
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
}

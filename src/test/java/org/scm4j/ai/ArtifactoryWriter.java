package org.scm4j.ai;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class ArtifactoryWriter {

	private static final String PRODUCT_LIST_DEFAULT_VERSION = "1.1.0";
	private File artifactoryFolder;
	private File productListFile;

	public ArtifactoryWriter(File artifactoryFolder) {
		this.artifactoryFolder = artifactoryFolder;
		artifactoryFolder.mkdirs();
		generateProductListArtifact();
	}

	private void generateProductListArtifact() {
		try {

			createProductListFileDirs();

			Metadata metaData = getProductListArtifactMetadata();

			writeProductListMetadata(metaData);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void writeProductListMetadata(Metadata metaData) throws Exception {
		File productListMetadataFolder = new File(artifactoryFolder,
				Utils.coordsToFolderStructure(ArtifactoryReader.PRODUCT_LIST_GROUP_ID, ArtifactoryReader.PRODUCT_LIST_ARTIFACT_ID));
		File productListMetadataFile = new File(productListMetadataFolder, "maven-metadata.xml");
		writeMetadata(metaData, productListMetadataFile);
	}

	private void writeMetadata(Metadata metaData, File metaDataFile) throws Exception {
		try (FileOutputStream os = new FileOutputStream(metaDataFile)) {
			MetadataXpp3Writer writter = new MetadataXpp3Writer();
			writter.write(os, metaData);
		}
	}

	private Metadata getProductListArtifactMetadata() {
		Metadata metaData = cereateArtifactMetadata(ArtifactoryReader.PRODUCT_LIST_GROUP_ID,
				ArtifactoryReader.PRODUCT_LIST_ARTIFACT_ID);
		List<String> versions = metaData.getVersioning().getVersions();
		versions.add(PRODUCT_LIST_DEFAULT_VERSION);
		metaData.getVersioning().setRelease(PRODUCT_LIST_DEFAULT_VERSION);
		return metaData;
	}

	private Metadata cereateArtifactMetadata(String groupId, String artifactId) {
		Metadata metaData = new Metadata();
		Versioning vers = new Versioning();
		metaData.setVersioning(vers);
		metaData.setGroupId(groupId);
		metaData.setArtifactId(artifactId);
		return metaData;
	}

	private void createProductListFileDirs() throws IOException {
		productListFile = new File(artifactoryFolder,
				Utils.coordsToRelativeFilePath(ArtifactoryReader.PRODUCT_LIST_GROUP_ID,
				ArtifactoryReader.PRODUCT_LIST_ARTIFACT_ID, PRODUCT_LIST_DEFAULT_VERSION, ".yml"));
		if (!productListFile.exists()) {
			productListFile.getParentFile().mkdirs();
			productListFile.createNewFile();
		}
	}

	public void installArtifact(String groupId, String artifactId, String version, String extension,
			String content, File productListLocation) {
		try {

			File artifactRoot = new File(artifactoryFolder, Utils.coordsToFolderStructure(groupId, artifactId));

			if(extension.equals(".yml")) {
				File artifactFile = writeProduct(artifactId,version,extension,artifactRoot);
			} else {
				File artifactFile = writeArtifact(artifactId, version, extension, content, artifactRoot);
			}

			appendMetadata(groupId, artifactId, version, artifactRoot);

			//TODO Product List only appends for products, not for Product Component Artifacts
			appendProductList(groupId, artifactId, version, extension, productListLocation);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private File writeArtifact(String artifactId, String version, String extension, String content, File artifactRoot)
			throws IOException {
		File artifactVersionPath = new File(artifactRoot, version);
		artifactVersionPath.mkdirs();

		File artifactFile = new File(artifactVersionPath, Utils.coordsToFileName(artifactId, version, extension));

		FileUtils.writeStringToFile(artifactFile, content);
		return artifactFile;
	}

	private File writeProduct(String artifactId, String version, String extension, File artifactRoot) throws IOException {
		File artifactVersionPath = new File(artifactRoot, version);
		artifactVersionPath.mkdirs();

		File artifactFile = new File(artifactVersionPath, Utils.coordsToFileName(artifactId, version, extension));

		artifactFile.createNewFile();

		return artifactFile;
	}

	private void appendProduct(String artifactId, String version) {
		Yaml yaml = new Yaml();
		Map<String, String> productContent;

	}

	//TODO write products yml's
	//TODO write Product Artifact Components pom's

	private void appendProductList(String groupId, String artifactId, String version, String extension,
			File productListLocation) throws Exception  {

		File remoteProductListFileLocation = new File(productListLocation,
				Utils.coordsToRelativeFilePath(ArtifactoryReader.PRODUCT_LIST_GROUP_ID,
				ArtifactoryReader.PRODUCT_LIST_ARTIFACT_ID, PRODUCT_LIST_DEFAULT_VERSION, ".yml"));

		Map<String, ArrayList<String>> products = getProductListContent(remoteProductListFileLocation);

        if (!products.get("products").contains(Utils.coordsToString(groupId, artifactId, version,extension))) {
				products.get("products").add(Utils.coordsToString(groupId, artifactId));
				remoteProductListFileLocation.delete();
				remoteProductListFileLocation.createNewFile();
				writeProductListContent(products, remoteProductListFileLocation);
        }
    }

	private void writeProductListContent(Map<String, ArrayList<String>> products, File remoteProductListFileLocation)
			throws Exception {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		Yaml yaml = new Yaml(options);
		String yamlOutput = yaml.dump(products);
		FileWriter fw = new FileWriter(remoteProductListFileLocation);
		fw.write(yamlOutput);
		fw.flush();
		fw.close();
	}

	private Map<String, ArrayList<String>> getProductListContent(File remoteProductListFileLocation)
			throws IOException{
		try (FileReader reader = new FileReader(remoteProductListFileLocation)) {
			Yaml yaml = new Yaml();
			@SuppressWarnings("unchecked")
			Map<String, ArrayList<String>> res = yaml.loadAs(reader, HashMap.class);
			if (res == null) {
				res = new HashMap<>();
				res.put("products",new ArrayList<>());
				res.put("repositories", new ArrayList<>());
			}
			return res;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void appendMetadata(String groupId, String artifactId, String version, File artifactRoot)
			throws Exception {
		File mavenMetadataFile = new File(artifactRoot, ArtifactoryReader.METADATA_FILE_NAME);

		Metadata metaData;
		if (!mavenMetadataFile.exists()) {
			metaData = cereateArtifactMetadata(groupId, artifactId);
		} else {
			metaData = readArtifactMetadata(mavenMetadataFile);
		}

		metaData.getVersioning().getVersions().add(version);

		writeMetadata(metaData, mavenMetadataFile);
	}

	private Metadata readArtifactMetadata(File mavenMetadataFile) throws Exception {
		try (InputStream is = mavenMetadataFile.toURI().toURL().openStream()) {
			MetadataXpp3Reader reader = new MetadataXpp3Reader();
			return reader.read(is);
		}
	}

}

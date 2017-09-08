package org.scm4j.ai;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class ArtifactoryWriter {

	public static final String PRODUCT_LIST_DEFAULT_VERSION = "1.1.0";
	public static final String PRODUCT_LIST_VERSION = "1.2.0";
	private static final String TEST_RESOURCES = "org/scm4j/ai/poms/";
	private File artifactoryFolder;
	private File productListFile;
	private static Yaml YAML;

	public ArtifactoryWriter(File artifactoryFolder) {
		this.artifactoryFolder = artifactoryFolder;
		artifactoryFolder.mkdirs();
	}

	public void generateProductListArtifact() {
		try {

//			createProductListFileDirs();

			Metadata metaData = getProductListArtifactMetadata();

			writeProductListMetadata(metaData);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void writeProductListMetadata(Metadata metaData) throws Exception {
		File productListMetadataFolder = new File(artifactoryFolder,
				Utils.coordsToFolderStructure(ProductList.PRODUCT_LIST_GROUP_ID, ProductList.PRODUCT_LIST_ARTIFACT_ID));
		File productListMetadataFile = new File(productListMetadataFolder, "maven-metadata.xml");
		writeMetadata(metaData, productListMetadataFile);
	}

	private void writeMetadata(Metadata metaData, File metaDataFile) throws Exception {
		try (FileOutputStream os = new FileOutputStream(metaDataFile)) {
			MetadataXpp3Writer writer = new MetadataXpp3Writer();
			writer.write(os, metaData);
		}
	}

	private Metadata getProductListArtifactMetadata() {
		Metadata metaData = createArtifactMetadata(ProductList.PRODUCT_LIST_GROUP_ID,
				ProductList.PRODUCT_LIST_ARTIFACT_ID);
		List<String> versions = metaData.getVersioning().getVersions();
		versions.add(PRODUCT_LIST_DEFAULT_VERSION);
		versions.add(PRODUCT_LIST_VERSION);
		metaData.getVersioning().setRelease(PRODUCT_LIST_DEFAULT_VERSION);
		return metaData;
	}

	private Metadata createArtifactMetadata(String groupId, String artifactId) {
		Metadata metaData = new Metadata();
		Versioning vers = new Versioning();
		metaData.setVersioning(vers);
		metaData.setGroupId(groupId);
		metaData.setArtifactId(artifactId);
		return metaData;
	}

	public void installArtifact(String groupId, String artifactId, String version, String extension,
			String content, File productListLocation) {
		try {

			File artifactRoot = new File(artifactoryFolder, Utils.coordsToFolderStructure(groupId, artifactId));

			writeArtifact(artifactId, version, extension, content, artifactRoot);

			appendMetadata(groupId, artifactId, version, artifactRoot);

			if(Utils.isYml(extension)) {
				appendProductList(groupId, artifactId, version, extension, productListLocation);
			} else if(!content.contains("dep")){
				appendProduct(groupId,artifactId,version,artifactId + "installer.jar",productListLocation);
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private File writeArtifact(String artifactId, String version, String extension, String content, File artifactRoot)
			throws IOException, URISyntaxException {
		File artifactVersionPath = new File(artifactRoot, version);
		artifactVersionPath.mkdirs();

		File artifactFile = new File(artifactVersionPath, Utils.coordsToFileName(artifactId, version, extension));

		File artifactPom = new File(artifactVersionPath, Utils.coordsToFileName(artifactId,version,".pom"));

		if(!Utils.isYml(extension)) {
			FileUtils.writeStringToFile(artifactFile, content);
		} else {
			artifactFile.createNewFile();
		}

		File resource = new File(getClass().getClassLoader().getResource(TEST_RESOURCES +
				Utils.coordsToFileName(artifactId,version,".pom")).getFile());

		Files.copy(resource, artifactPom);

		return artifactFile;
	}

	private void appendProduct(String groupId,String artifactId, String version, String installer, File productFile) throws IOException {
		YAML = new Yaml();
		Map<String,String> res;
		try(FileReader is = new FileReader(productFile)) {
			res = YAML.loadAs(is, HashMap.class);
		}
		if (res == null) {
			res = new HashMap<>();
		}
		res.put(Utils.coordsToString(groupId,artifactId, version), installer);
		String yamlOutput = YAML.dumpAsMap(res);
		FileWriter writer = new FileWriter(productFile);
		writer.write(yamlOutput);
		writer.flush();
		writer.close();
	}

	private void appendProductList(String groupId, String artifactId, String version, String extension,
			File productListLocation) throws Exception  {

		File remoteProductListFileLocation = new File(productListLocation,
				Utils.coordsToRelativeFilePath(ProductList.PRODUCT_LIST_GROUP_ID,
						ProductList.PRODUCT_LIST_ARTIFACT_ID, PRODUCT_LIST_DEFAULT_VERSION, ".yml"));

		Map<String, ArrayList<String>> products = getProductListContent(remoteProductListFileLocation);

        if (!products.get(ProductList.PRODUCTS).contains(Utils.coordsToString(groupId, artifactId, version,extension))) {
				products.get(ProductList.PRODUCTS).add(Utils.coordsToString(groupId, artifactId));
				remoteProductListFileLocation.delete();
				remoteProductListFileLocation.createNewFile();
				writeProductListContent(products, remoteProductListFileLocation);
        }
    }

	private void writeProductListContent(Map<String, ArrayList<String>> products, File remoteProductListFileLocation)
			throws Exception {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		YAML = new Yaml(options);
		String yamlOutput = YAML.dump(products);
		FileWriter fw = new FileWriter(remoteProductListFileLocation);
		fw.write(yamlOutput);
		fw.flush();
		fw.close();
	}

	private Map<String, ArrayList<String>> getProductListContent(File remoteProductListFileLocation)
			throws IOException{
		try (FileReader reader = new FileReader(remoteProductListFileLocation)) {
			YAML = new Yaml();
			@SuppressWarnings("unchecked")
			Map<String, ArrayList<String>> res = YAML.loadAs(reader, HashMap.class);
			if (res == null) {
				res = new HashMap<>();
				res.put(ProductList.PRODUCTS,new ArrayList<>());
				res.put(ProductList.REPOSITORIES, new ArrayList<>());
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
			metaData = createArtifactMetadata(groupId, artifactId);
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

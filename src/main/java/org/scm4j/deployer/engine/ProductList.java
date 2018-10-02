package org.scm4j.deployer.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.scm4j.deployer.engine.exceptions.ENoMetadata;
import org.scm4j.deployer.engine.exceptions.EProductListEntryNotFound;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Data
class ProductList {

	public static final String PRODUCT_LIST_GROUP_ID = "org.scm4j.ai";
	public static final String PRODUCT_LIST_ARTIFACT_ID = "product-list";
	public static final String REPOSITORIES = "Repositories";
	public static final String PRODUCTS = "Products";
	public static final String VERSIONS_ARTIFACT_ID = "products-versions.json";
	private final ArtifactoryReader productListReader;
	private final File localRepo;
	private List<ArtifactoryReader> repos;
	private Map<String, String> products;
	private File localProductList;
	private File versionsJson;
	private Map productListEntry;
	private Map<String, Set<String>> productsVersions;
	private Type versionsJsonType;
	private Gson gson;

	ProductList(File localRepo, ArtifactoryReader productListReader) {
		this.localRepo = localRepo;
		this.productListReader = productListReader;
		this.versionsJsonType = new TypeToken<Map<String, Set<String>>>() {
		}.getType();
		gson = new GsonBuilder().setPrettyPrinting().create();
	}

	Map readFromProductList() throws Exception {
		String productListReleaseVersion = getLocalProductListReleaseVersion();
		if (productListReleaseVersion == null) {
			downloadProductList();
			loadProductListEntry();
			downloadProductsVersions();
		} else {
			localProductList = new File(localRepo, Utils.coordsToRelativeFilePath(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
					productListReleaseVersion, ".json", null));
			versionsJson = new File(localRepo, VERSIONS_ARTIFACT_ID);
			loadProductListEntry();
		}
		return productListEntry;
	}

	void downloadProductList() throws Exception {
		String productListReleaseVersion = productListReader.getProductListReleaseVersion();
		String productListPath = Utils.coordsToRelativeFilePath(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
				productListReleaseVersion, ".json", null);
		URL remoteProductListUrl = new URL(productListReader.getUrl(), productListPath.replace("\\", File.separator));
		localProductList = new File(localRepo, productListPath);
		if (!localProductList.exists()) {
			localProductList.getParentFile().mkdirs();
			localProductList.createNewFile();
		}
		FileUtils.copyURLToFile(remoteProductListUrl, localProductList);
		writeProductListMetadata(productListReleaseVersion);
	}

	@SuppressWarnings("unchecked")
	private void loadProductListEntry() {
		try {
			productListEntry = gson.fromJson(FileUtils.readFileToString(localProductList, "UTF-8"), Map.class);
		} catch (IOException e) {
			throw new EProductListEntryNotFound(e);
		}
		repos = new ArrayList<>();
		((List<String>) productListEntry.get(REPOSITORIES)).forEach(name -> repos.add(ArtifactoryReader.getByUrl(name)));
		products = new HashMap<>();
		products.putAll((Map<String, String>) productListEntry.get(PRODUCTS));
	}

	private void downloadProductsVersions() throws IOException {
		versionsJson = new File(localRepo, VERSIONS_ARTIFACT_ID);
		productsVersions = new HashMap<>();
		for (Map.Entry<String, String> product : products.entrySet()) {
			Set<String> vers = new HashSet<>();
			for (ArtifactoryReader reader : repos) {
				vers.addAll(reader.getProductVersions(product.getValue()));
			}
			productsVersions.put(product.getKey(), vers);
		}
		@Cleanup
		FileWriter writer = new FileWriter(versionsJson);
		gson.toJson(productsVersions, writer);
	}

	@SuppressWarnings("unchecked")
	@SneakyThrows
	Map<String, Set<String>> readProductVersions(String artifactId) {
		try {
			productsVersions = gson.fromJson(FileUtils.readFileToString(versionsJson, "UTF-8"), versionsJsonType);
		} catch (NullPointerException e) {
			throw new EProductListEntryNotFound("Can't find product list");
		} catch (FileNotFoundException e1) {
			productsVersions = new HashMap<>();
		}
		new TreeSet<>().addAll(productsVersions.getOrDefault(artifactId, new TreeSet<>()));
		return productsVersions;
	}

	@SuppressWarnings("unchecked")
	void refreshProductVersions(String simpleName) throws IOException {
		String groupIdAndArtId = getProducts().getOrDefault(simpleName, "");
		try {
			productsVersions = gson.fromJson(FileUtils.readFileToString(versionsJson, "UTF-8"), versionsJsonType);
		} catch (FileNotFoundException e) {
			productsVersions = new HashMap<>();
		}
		Set<String> vers = new TreeSet<>();
		for (ArtifactoryReader reader : repos) {
			vers.addAll(reader.getProductVersions(groupIdAndArtId));
		}
		if (vers.isEmpty()) {
			throw new ENoMetadata(simpleName + " metadata don't find in all known repos");
		} else {
			productsVersions.put(simpleName, vers);
			@Cleanup
			FileWriter writer = new FileWriter(versionsJson);
			gson.toJson(productsVersions, writer);
		}
	}

	@SneakyThrows
	private String getLocalProductListReleaseVersion() {
		File metadataFolder = new File(localRepo, Utils.coordsToFolderStructure(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID));
		File metadataFile = new File(metadataFolder, ArtifactoryReader.METADATA_FILE_NAME);
		if (metadataFile.exists()) {
			MetadataXpp3Reader reader = new MetadataXpp3Reader();
			@Cleanup
			FileInputStream fis = new FileInputStream(metadataFile);
			Metadata meta = reader.read(fis);
			Versioning vers = meta.getVersioning();
			return vers.getRelease();
		} else {
			return null;
		}
	}

	@SneakyThrows
	private void writeProductListMetadata(String productListReleaseVersion) {
		File productListMetadataFolder = new File(localRepo,
				Utils.coordsToFolderStructure(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID));
		File productListMetadataFile = new File(productListMetadataFolder, "maven-metadata.xml");
		if (productListMetadataFile.exists())
			productListMetadataFile.delete();
		productListMetadataFile.createNewFile();
		Metadata metadata = new Metadata();
		Versioning vers = new Versioning();
		metadata.setVersioning(vers);
		metadata.setGroupId(ProductList.PRODUCT_LIST_GROUP_ID);
		metadata.setArtifactId(ProductList.PRODUCT_LIST_ARTIFACT_ID);
		metadata.getVersioning().setRelease(productListReleaseVersion);
		try (FileOutputStream os = new FileOutputStream(productListMetadataFile)) {
			MetadataXpp3Writer writer = new MetadataXpp3Writer();
			writer.write(os, metadata);
		}
	}
}

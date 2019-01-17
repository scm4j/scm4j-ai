package org.scm4j.deployer.engine;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.scm4j.deployer.api.ProductInfo;
import org.scm4j.deployer.engine.exceptions.ENoMetadata;
import org.scm4j.deployer.engine.exceptions.EProductListEntryNotFound;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
	public static final String VERSIONS_ARTIFACT_ID = "products-versions.json";
	private final ArtifactoryReader productListReader;
	private final File localRepo;
	private List<ArtifactoryReader> repos;
	private Map<String, ProductInfo> products;
	private File localProductList;
	private File versionsJson;
	private ProductListEntry productListEntry;
	private Map<String, Set<String>> productsVersions;
	private Type versionsJsonType;

	ProductList(File localRepo, ArtifactoryReader productListReader) {
		this.localRepo = localRepo;
		this.productListReader = productListReader;
		this.versionsJsonType = new TypeToken<Map<String, Set<String>>>() {
		}.getType();
	}

	ProductListEntry readFromProductList() throws Exception {
		String productListReleaseVersion = getLocalProductListReleaseVersion();
		if (productListReleaseVersion == null) {
			downloadProductList();
			loadProductListEntry();
			downloadProductsVersions();
		} else {
			localProductList = new File(localRepo, Utils.coordsToRelativeFilePath(PRODUCT_LIST_GROUP_ID,
					PRODUCT_LIST_ARTIFACT_ID, productListReleaseVersion, ".json", null));
			versionsJson = new File(localRepo, VERSIONS_ARTIFACT_ID);
			loadProductListEntry();
		}
		return productListEntry;
	}

	void downloadProductList() throws Exception {
		String productListReleaseVersion = productListReader.getProductListReleaseVersion();
		String productListPath = Utils.coordsToRelativeFilePath(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
				productListReleaseVersion, ".json", null);
		URL remoteProductListUrl = new URL(productListReader.getUrl(), productListPath.replace('\\', '/'));
		localProductList = new File(localRepo, productListPath);
		if (!localProductList.exists()) {
			localProductList.getParentFile().mkdirs();
			localProductList.createNewFile();
		}
		FileUtils.copyURLToFile(remoteProductListUrl, localProductList);
		writeProductListMetadata(productListReleaseVersion);
	}

	private void loadProductListEntry() {
		try {
			productListEntry = new Gson().fromJson(FileUtils.readFileToString(localProductList, "UTF-8"),
					ProductListEntry.class);
		} catch (IOException e) {
			throw new EProductListEntryNotFound(e);
		}
		repos = new ArrayList<>();
		productListEntry.getRepositories().forEach(name -> repos.add(ArtifactoryReader.getByUrl(name)));
		products = new HashMap<>();
		Map<String, ProductInfo> fromEntry = productListEntry.getProducts();
		products.putAll(fromEntry);
	}

	private void downloadProductsVersions() throws IOException {
		versionsJson = new File(localRepo, VERSIONS_ARTIFACT_ID);
		productsVersions = new HashMap<>();
		for (Map.Entry<String, ProductInfo> product : products.entrySet()) {
			Set<String> vers = new HashSet<>();
			for (ArtifactoryReader reader : repos) {
				vers.addAll(reader.getProductVersions(product.getValue().getArtifactId()));
			}
			productsVersions.put(product.getKey(), vers);
		}
		Utils.writeJson(productsVersions, versionsJson);
	}

	@SneakyThrows
	Map<String, Set<String>> readProductVersions(String artifactId) {
		try {
			productsVersions = Utils.readJson(versionsJson, versionsJsonType);
		} catch (NullPointerException e) {
			throw new EProductListEntryNotFound("Can't find product list");
		}
		new TreeSet<>().addAll(productsVersions.getOrDefault(artifactId, new TreeSet<>()));
		return productsVersions;
	}

	void refreshProductVersions(String simpleName) throws IOException {
		String groupIdAndArtId = getProducts().getOrDefault(simpleName, new ProductInfo("", false)).getArtifactId();
		productsVersions = Utils.readJson(versionsJson, versionsJsonType);
		Set<String> vers = new TreeSet<>();
		for (ArtifactoryReader reader : repos) {
			vers.addAll(reader.getProductVersions(groupIdAndArtId));
		}
		if (vers.isEmpty()) {
			throw new ENoMetadata(simpleName + " metadata don't find in all known repos");
		} else {
			productsVersions.put(simpleName, vers);
			Utils.writeJson(productsVersions, versionsJson);
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

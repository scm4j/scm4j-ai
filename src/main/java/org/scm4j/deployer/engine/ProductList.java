package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.util.*;

@Data
public class ProductList {

    private ArtifactoryReader productListReader;
    private List<ArtifactoryReader> repos;
    private Set<String> products;
    private List<String> versions;
    private List<String> downloadedProducts;
    private File localRepo;
    private File localProductList;
    private File versionsYml;
    private Map<String, List<String>> productListEntry;
    private Map<String, List<String>> productsVersions;

    public static final String PRODUCT_LIST_GROUP_ID = "org.scm4j.ai";
    public static final String PRODUCT_LIST_ARTIFACT_ID = "product-list";
    public static final String REPOSITORIES = "Repositories";
    public static final String PRODUCTS = "Products";
    public static final String VERSIONS = "Versions";
    public static final String VERSIONS_ARTIFACT_ID = "products-versions.yml";
    public static final String DOWNLOADED_PRODUCTS = "downloaded products";

    public ProductList(File localRepo, ArtifactoryReader productListReader) {
        this.localRepo = localRepo;
        this.productListReader = productListReader;
    }

    @SneakyThrows
    public Map<String, List<String>> readFromProductList() {
        String productListReleaseVersion = getLocalProductListReleaseVersion();
        if (productListReleaseVersion == null) {
            downloadProductList();
            loadProductListEntry();
            downloadProductsVersions();
            return productListEntry;
        } else {
            localProductList = new File(localRepo, Utils.coordsToRelativeFilePath(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
                    productListReleaseVersion, ".yml"));
            versionsYml = new File(getLocalProductList().getParent(), VERSIONS_ARTIFACT_ID);
            loadProductListEntry();
            return productListEntry;
        }
    }

    public Map<String, List<String>> readProductVersions(String groupId, String artifactId) {
        if(versionsYml.isFile()) {
            loadProductVersions(artifactId);
            return productsVersions;
        } else {
            downloadSingleProjectVersions(groupId, artifactId);
            loadProductVersions(artifactId);
            return productsVersions;
        }
    }

    private void downloadProductsVersions() {
        versionsYml = new File(getLocalProductList().getParent(), VERSIONS_ARTIFACT_ID);
        productsVersions = new HashMap<>();
        for (String product : products) {
            String artifactId = StringUtils.substringAfter(product, ":");
            List<String> versions = getProductVersions(StringUtils.substringBefore(product, ":"),
                    artifactId);
            productsVersions.put(artifactId, versions);
        }
        yamlWriter(productsVersions, versionsYml);
    }

    private void downloadSingleProjectVersions(String groupId, String artifactId) {
        productsVersions = readYml(versionsYml);
        List<String> versions = getProductVersions(groupId, artifactId);
        productsVersions.get(artifactId).clear();
        productsVersions.put(artifactId, versions);
        yamlWriter(productsVersions, versionsYml);
    }

    private void loadProductVersions(String artifactId) {
        productsVersions = readYml(versionsYml);
        versions = new ArrayList<>();
        versions.addAll(productsVersions.get(artifactId));
    }

    public void refreshProductVersions(String groupId, String artifactId) {
        productsVersions = readYml(versionsYml);
        productsVersions.get(artifactId).clear();
        productsVersions.get(artifactId).addAll(getProductVersions(groupId, artifactId));
        yamlWriter(productsVersions, versionsYml);
    }

    @SneakyThrows
    public String getLocalProductListReleaseVersion() {
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

    public void downloadProductList() throws Exception {
        String productListReleaseVersion = productListReader.getProductListReleaseVersion();
        URL remoteProductListUrl = productListReader.getProductUrl(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
                productListReleaseVersion, ".yml");
        localProductList = new File(localRepo, Utils.coordsToRelativeFilePath(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
                productListReleaseVersion, ".yml"));
        if (!localProductList.exists()) {
            localProductList.getParentFile().mkdirs();
            localProductList.createNewFile();
        }
        FileUtils.copyURLToFile(remoteProductListUrl, localProductList);
        writeProductListMetadata(productListReleaseVersion);
    }

    private void loadProductListEntry() {
        productListEntry = readYml(localProductList);
        repos = new ArrayList<>();
        productListEntry.get(REPOSITORIES).forEach(name -> repos.add(ArtifactoryReader.getByUrl(name)));
        products = new HashSet<>();
        products.addAll(productListEntry.get(PRODUCTS));
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    private Map<String, List<String>> readYml(File input) {
        @Cleanup
        FileReader reader = new FileReader(input);
        Yaml yaml = new Yaml();
        return yaml.loadAs(reader, HashMap.class);
    }

    @SneakyThrows
    public void appendLocalRepo() {
        productListEntry.get(REPOSITORIES).add(localRepo.toURI().toURL().toString());
        yamlWriter(productListEntry, localProductList);
    }

    @SneakyThrows
    private void yamlWriter(Map<String, List<String>> entry, File output) {
        @Cleanup
        FileWriter writer = new FileWriter(output);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        String yamlOutput = yaml.dump(entry);
        writer.write(yamlOutput);
    }

    @SneakyThrows
    public List<String> getProductVersions(String groupId, String artifactId) {
        if (!hasProduct(groupId, artifactId)) {
            throw new EProductNotFound();
        }
        MetadataXpp3Reader reader = new MetadataXpp3Reader();
        try (InputStream is = productListReader.getProductMetaDataURL(groupId, artifactId).openStream()) {
            Metadata meta = reader.read(is);
            Versioning vers = meta.getVersioning();
            return vers.getVersions();
        }
    }

    public boolean hasProduct(String groupId, String artifactId) throws Exception {
        return getProducts().contains(Utils.coordsToString(groupId, artifactId));
    }

    private void writeProductListMetadata(String productListReleaseVersion) throws Exception {
        File productListMetadataFolder = new File(localRepo,
                Utils.coordsToFolderStructure(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID));
        File productListMetadataFile = new File(productListMetadataFolder, "maven-metadata.xml");
        if (productListMetadataFile.exists()) {
            productListMetadataFile.delete();
        }
        productListMetadataFile.createNewFile();
        Metadata metadata = createArtifactMetadata(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID);
        metadata.getVersioning().setRelease(productListReleaseVersion);
        writeMetadata(metadata, productListMetadataFile);
    }

    private void writeMetadata(Metadata metaData, File metaDataFile) throws Exception {
        try (FileOutputStream os = new FileOutputStream(metaDataFile)) {
            MetadataXpp3Writer writer = new MetadataXpp3Writer();
            writer.write(os, metaData);
        }
    }

    private Metadata createArtifactMetadata(String groupId, String artifactId) {
        Metadata metaData = new Metadata();
        Versioning vers = new Versioning();
        metaData.setVersioning(vers);
        metaData.setGroupId(groupId);
        metaData.setArtifactId(artifactId);
        return metaData;
    }
}

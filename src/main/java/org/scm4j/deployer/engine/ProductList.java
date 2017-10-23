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
import org.scm4j.deployer.engine.exceptions.ENoMetadata;

import java.io.*;
import java.net.URL;
import java.util.*;

@Data
public class ProductList {

    private ArtifactoryReader productListReader;
    private Set<ArtifactoryReader> repos;
    private Set<String> products;
    private Set<String> versions;
    private List<String> downloadedProducts;
    private File localRepo;
    private File localProductList;
    private File versionsYml;
    private Map<String, Set<String>> productListEntry;
    private Map<String, Set<String>> productsVersions;

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


    public Map<String, Set<String>> readFromProductList() throws Exception {
        String productListReleaseVersion = getLocalProductListReleaseVersion();
        if (productListReleaseVersion == null) {
            downloadProductList();
            loadProductListEntry();
            try {
                downloadProductsVersions();
            } catch (ENoMetadata e) {
                versionsYml = new File(localRepo, VERSIONS_ARTIFACT_ID);
                versionsYml.createNewFile();
            }
            return productListEntry;
        } else {
            localProductList = new File(localRepo, Utils.coordsToRelativeFilePath(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
                    productListReleaseVersion, ".yml"));
            versionsYml = new File(localRepo, VERSIONS_ARTIFACT_ID);
            loadProductListEntry();
            return productListEntry;
        }
    }

    //TODO refactor this
    public Map<String, Set<String>> readProductVersions(String artifactId) {
        loadProductVersions(artifactId);
        return productsVersions;
    }

    @SneakyThrows
    private void downloadProductsVersions() throws ENoMetadata {
        versionsYml = new File(localRepo, VERSIONS_ARTIFACT_ID);
        productsVersions = new HashMap<>();
        Set<String> vers = new HashSet<>();
        for (String product : products) {
            String artifactId = StringUtils.substringAfter(product, ":");
            for (ArtifactoryReader reader : repos) {
                vers.addAll(reader.getProductVersions(StringUtils.substringBefore(product, ":"), artifactId));
            }
            productsVersions.put(artifactId, vers);
        }
        Utils.writeYaml(productsVersions, versionsYml);
    }

    private void loadProductVersions(String artifactId) {
        productsVersions = Utils.readYml(versionsYml);
        if (productsVersions == null)
            productsVersions = new HashMap<>();
        versions = new HashSet<>();
        versions.addAll(productsVersions.getOrDefault(artifactId, new HashSet<>()));
    }

    public void refreshProductVersions(String groupId, String artifactId) throws ENoMetadata {
        productsVersions = Utils.readYml(versionsYml);
        if (productsVersions == null) {
            productsVersions = new HashMap<>();
        }
        Set<String> vers = new HashSet<>();
        for (ArtifactoryReader reader : repos) {
            vers.addAll(reader.getProductVersions(groupId, artifactId));
        }
        if (vers.isEmpty()) {
            throw new ENoMetadata(artifactId + " metadata don't find in all known repos");
        } else {
            productsVersions.put(artifactId, vers);
            Utils.writeYaml(productsVersions, versionsYml);
        }
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
        productListEntry = Utils.readYml(localProductList);
        repos = new LinkedHashSet<>();
        productListEntry.get(REPOSITORIES).forEach(name -> repos.add(ArtifactoryReader.getByUrl(name)));
        products = new HashSet<>();
        products.addAll(productListEntry.get(PRODUCTS));
    }

    @SneakyThrows
    public void appendLocalRepo() {
        productListEntry.get(REPOSITORIES).add(localRepo.toURI().toURL().toString());
        Utils.writeYaml(productListEntry, localProductList);
    }

    @SneakyThrows
    private void writeProductListMetadata(String productListReleaseVersion) {
        File productListMetadataFolder = new File(localRepo,
                Utils.coordsToFolderStructure(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID));
        File productListMetadataFile = new File(productListMetadataFolder, "maven-metadata.xml");
        if (productListMetadataFile.exists()) {
            productListMetadataFile.delete();
        }
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

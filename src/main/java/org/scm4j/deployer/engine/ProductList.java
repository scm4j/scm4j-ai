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
    private List<String> downloadedProducts;
    private File localRepo;
    private File localProductList;
    private Map<String, ArrayList<String>> productListEntry;

    public static final String PRODUCT_LIST_GROUP_ID = "org.scm4j.ai";
    public static final String PRODUCT_LIST_ARTIFACT_ID = "product-list";
    public static final String REPOSITORIES = "repositories";
    public static final String PRODUCTS = "available products";
    public static final String DOWNLOADED_PRODUCTS = "downloaded products";

    public ProductList(File localRepo) {
        this.localRepo = localRepo;
    }

    public void downloadProductList(String productListArtifactoryUrl, String userName, String password) throws Exception {
        productListReader = new ArtifactoryReader(productListArtifactoryUrl, userName, password);
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
        loadProductListEntry();
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    private void loadProductListEntry() {
        @Cleanup
        FileReader reader = new FileReader(localProductList);
        Yaml yaml = new Yaml();
        productListEntry = yaml.loadAs(reader, HashMap.class);
        List<String> reposNames = productListEntry.get(REPOSITORIES);
        for (String repoName : reposNames) {
            if (repos == null) {
                repos = new ArrayList<>();
                repos.add(ArtifactoryReader.getByUrl(repoName));
            } else {
                repos.add(ArtifactoryReader.getByUrl(repoName));
            }
        }
        products = new HashSet<>();
        products.addAll(productListEntry.get(PRODUCTS));
    }

    @SneakyThrows
    public void appendLocalRepo() {
        productListEntry.get(REPOSITORIES).add(localRepo.toURI().toURL().toString());
        yamlWriter(productListEntry);
    }

    public void markDownloadedProduct(String artifactId, String version) {
        String newProduct = productListEntry.get(PRODUCTS).stream()
                .filter(s -> s.contains(artifactId))
                .map(s -> s = StringUtils.substringAfter(s, ":") + "-" + version)
                .findFirst().orElse(null);
        productListEntry.computeIfAbsent(DOWNLOADED_PRODUCTS, list -> new ArrayList<>())
                .add(newProduct);
        yamlWriter(productListEntry);
    }

    @SneakyThrows
    private void yamlWriter(Map<String, ArrayList<String>> entry) {
        @Cleanup
        FileWriter writer = new FileWriter(localProductList);
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

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
import org.scm4j.deployer.engine.exceptions.EProductNotFound;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

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


    public Map<String, List<String>> readFromProductList() throws Exception {
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
    public Map<String, List<String>> readProductVersions(String groupId, String artifactId) {
        loadProductVersions(artifactId);
        return productsVersions;
    }

    @SneakyThrows
    private void downloadProductsVersions() throws ENoMetadata {
        versionsYml = new File(localRepo, VERSIONS_ARTIFACT_ID);
        productsVersions = new HashMap<>();
        List<String> vers = new ArrayList<>();
        for (String product : products) {
            String artifactId = StringUtils.substringAfter(product, ":");
            for (ArtifactoryReader reader : repos) {
                vers.addAll(reader.getProductVersions(StringUtils.substringBefore(product, ":"), artifactId));
            }
            productsVersions.put(artifactId, vers);
        }
        yamlWriter(productsVersions, versionsYml);
    }

    private void loadProductVersions(String artifactId) {
        productsVersions = readYml(versionsYml);
        if (productsVersions == null)
            productsVersions = new HashMap<>();
        versions = new ArrayList<>();
        versions.addAll(productsVersions.getOrDefault(artifactId, new ArrayList<>()));
    }

    public void refreshProductVersions(String groupId, String artifactId) throws ENoMetadata {
        productsVersions = readYml(versionsYml);
        if (productsVersions == null) {
            productsVersions = new HashMap<>();
        }
        List<String> vers = new ArrayList<>();
        for(ArtifactoryReader reader : repos) {
            vers.addAll(reader.getProductVersions(groupId, artifactId));
        }
        if (vers.isEmpty()) {
            throw new ENoMetadata(artifactId + " metadata don't find in all known repos");
        } else {
            productsVersions.put(artifactId, vers);
            yamlWriter(productsVersions, versionsYml);
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
        productListEntry = readYml(localProductList);
        repos = new ArrayList<>();
        productListEntry.get(REPOSITORIES).forEach(name -> repos.add(ArtifactoryReader.getByUrl(name)));
        products = new HashSet<>();
        products.addAll(productListEntry.get(PRODUCTS));
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    private Map<String, List<String>> readYml(File input) {
        if (input.exists()) {
            @Cleanup
            FileReader reader = new FileReader(input);
            Yaml yaml = new Yaml();
            return yaml.loadAs(reader, HashMap.class);
        } else {
            return new HashMap<>();
        }
    }

    @SneakyThrows
    public void appendLocalRepo() {
        if (!productListEntry.get(REPOSITORIES).contains(localRepo.toURI().toURL().toString()))
            productListEntry.get(REPOSITORIES).add(0, localRepo.toURI().toURL().toString());
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

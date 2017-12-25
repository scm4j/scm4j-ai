package org.scm4j.deployer.engine;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

class ArtifactoryWriter {

    static final String PRODUCT_LIST_DEFAULT_VERSION = "1.1.0";
    static final String PRODUCT_LIST_VERSION = "1.2.0";
    private static final String TEST_POMS = "org/scm4j/deployer/engine/poms/";
    private static final String TEST_CLASS = "org/scm4j/deployer/engine/testclasses/";
    private static Yaml YAML;
    private final File artifactoryFolder;

    ArtifactoryWriter(File artifactoryFolder) {
        this.artifactoryFolder = artifactoryFolder;
        artifactoryFolder.mkdirs();
    }

    void generateProductListArtifact() {
        try {
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
        Set<String> versions = new HashSet<>(metaData.getVersioning().getVersions());
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

    void installArtifact(String groupId, String artifactId, String version, String extension,
                         String content, File productListLocation) {
        try {
            File artifactRoot = new File(artifactoryFolder, Utils.coordsToFolderStructure(groupId, artifactId));
            writeArtifact(artifactId, version, extension, content, artifactRoot);
            appendMetadata(groupId, artifactId, version, artifactRoot);
            if (content.contains("Structure")) {
                appendProductList(groupId, artifactId, productListLocation);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeArtifact(String artifactId, String version, String extension, String content, File artifactRoot)
            throws Exception {
        File artifactVersionPath = new File(artifactRoot, version);
        artifactVersionPath.mkdirs();
        File artifactFile = new File(artifactVersionPath, Utils.coordsToFileName(artifactId, version, extension));
        artifactFile.createNewFile();
        File artifactPom = new File(artifactVersionPath, Utils.coordsToFileName(artifactId, version, ".pom"));
        if (content.contains("Structure") || content.contains("Executor"))
            createProductJar(content, artifactFile);
        else
            FileUtils.writeStringToFile(artifactFile, content, Charset.forName("UTF-8"));
        File resource = new File(getClass().getClassLoader().getResource(TEST_POMS +
                Utils.coordsToFileName(artifactId, version, ".pom")).getFile());
        Files.copy(resource, artifactPom);
    }

    private void createProductJar(String className, File artifactFile) throws Exception {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, className);
        File testProduct = new File(getClass().getClassLoader().getResource(TEST_CLASS + className + ".class").getFile());
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(artifactFile), mf);
             BufferedInputStream bin = new BufferedInputStream(new FileInputStream(testProduct))) {
            JarEntry entry = new JarEntry(className + ".class");
            entry.setTime(testProduct.lastModified());
            jos.putNextEntry(entry);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = bin.read(buffer)) != -1) {
                jos.write(buffer, 0, len);
            }
            jos.closeEntry();
        }
    }

    @SuppressWarnings("unchecked")
    private void appendProductList(String groupId, String artifactId, File productListLocation) throws Exception {
        File remoteProductListFileLocation = new File(productListLocation,
                Utils.coordsToRelativeFilePath(ProductList.PRODUCT_LIST_GROUP_ID,
                        ProductList.PRODUCT_LIST_ARTIFACT_ID, PRODUCT_LIST_DEFAULT_VERSION, ".yml"));
        Map products = getProductListContent(remoteProductListFileLocation);
        if (!((Map) products.get(ProductList.PRODUCTS)).keySet().contains(groupId + ":" + artifactId)) {
            ((Map) products.get(ProductList.PRODUCTS)).put(groupId + ":" + artifactId, artifactId);
            remoteProductListFileLocation.delete();
            remoteProductListFileLocation.createNewFile();
            writeProductListContent(products, remoteProductListFileLocation);
        }
    }

    private void writeProductListContent(Map products, File remoteProductListFileLocation)
            throws Exception {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        YAML = new Yaml(options);
        String yamlOutput = YAML.dump(products);
        try (FileWriter fw = new FileWriter(remoteProductListFileLocation)) {
            fw.write(yamlOutput);
        }
    }

    @SuppressWarnings("unchecked")
    private Map getProductListContent(File remoteProductListFileLocation) {
        try (FileReader reader = new FileReader(remoteProductListFileLocation)) {
            YAML = new Yaml();
            Map res = YAML.loadAs(reader, HashMap.class);
            if (res == null) {
                res = new HashMap<>();
                res.put((ProductList.PRODUCTS), new HashMap<>());
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

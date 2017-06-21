package org.scm4j.ai;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;

import com.google.common.base.Joiner;

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
				ArtifactoryReader.PRODUCT_LIST_ARTIFACT_ID, PRODUCT_LIST_DEFAULT_VERSION, ".zip"));
		if (!productListFile.exists()) {
			productListFile.getParentFile().mkdirs();
			productListFile.createNewFile();
		}
	}
	
	public void install(String groupId, String artifactId, String version, String extension, 
			String content, File productListLocation) {
		try {
			
			File artifactRoot = new File(artifactoryFolder, Utils.coordsToFolderStructure(groupId, artifactId));
			
			writeArtifact(artifactId, version, extension, content, artifactRoot);
			
			appendMetadata(groupId, artifactId, version, artifactRoot);
			
			appendProductList(groupId, artifactId, version, extension, productListLocation);
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void writeArtifact(String artifactId, String version, String extension, String content, File artifactRoot)
			throws IOException {
		File artifactVersionPath = new File(artifactRoot, version);
		artifactVersionPath.mkdirs();
		
		File artifactFile = new File(artifactVersionPath, Utils.coordsToFileName(artifactId, version, extension));
		FileUtils.writeStringToFile(artifactFile, content);
	}

	private void appendProductList(String groupId, String artifactId, String version, String extension, 
			File productListLocation) throws Exception  {
		
		File remoteProductListFileLocation = new File(productListLocation, 
				Utils.coordsToRelativeFilePath(ArtifactoryReader.PRODUCT_LIST_GROUP_ID, 
				ArtifactoryReader.PRODUCT_LIST_ARTIFACT_ID, PRODUCT_LIST_DEFAULT_VERSION, ".zip"));
		
		Map<String, String> products = getProductListContent(remoteProductListFileLocation);
		
		if (!products.containsKey(Utils.coordsToString(groupId, artifactId))) {
			products.put(Utils.coordsToString(groupId, artifactId), artifactoryFolder.toURI().toURL().toString());
			writeProductListContent(products, remoteProductListFileLocation);
		}
	}

	private void writeProductListContent(Map<String, String> products, File remoteProductListFileLocation)
			throws Exception {
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(remoteProductListFileLocation, true))) {
			ZipEntry productsEntry = new ZipEntry(ArtifactoryReader.PRODUCT_LIST_FILE_NAME);
			zos.putNextEntry(productsEntry);
			try {
				zos.write(Joiner.on("\r\n").withKeyValueSeparator("=").join(products).getBytes());
			} finally {
				zos.closeEntry();
			}
		}
	}

	private Map<String, String> getProductListContent(File remoteProductListFileLocation)
			throws IOException, FileNotFoundException {
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(remoteProductListFileLocation))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().equals(ArtifactoryReader.PRODUCT_LIST_FILE_NAME)) {
					return Utils.readMap(zis);
				}
			}
		}
		return new HashMap<>();
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

package org.scm4j.ai;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class ArtifactoryWriter {

	private File artifactoryFolder;
	private File productListFile;

	public ArtifactoryWriter(File artifactoryFolder) {
		this.artifactoryFolder = artifactoryFolder;
		try {
			artifactoryFolder.mkdirs();
			productListFile = new File(artifactoryFolder, "org" + File.separator + "scm4j" + File.separator 
					+ ArtifactoryReader.PRODUCT_LIST_FILE_NAME + File.separator 
					+ ArtifactoryReader.PRODUCT_LIST_FILE_NAME);
			if (!productListFile.exists()) {
				productListFile.getParentFile().mkdirs();
				productListFile.createNewFile();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void install(String groupId, String artifactId, String version, String extension, 
			String content) {
		try {
			
			File artifactRoot = new File(artifactoryFolder, Utils.coordsToFolderStructure(groupId, artifactId));
			
			saveArtifact(artifactId, version, extension, content, artifactRoot);
			
			appendMetadata(groupId, artifactId, version, artifactRoot);
			
			appendProductList(groupId, artifactId, version, extension);
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void saveArtifact(String artifactId, String version, String extension, String content, File artifactRoot)
			throws IOException {
		File artifactVersionPath = new File(artifactRoot, version);
		artifactVersionPath.mkdirs();
		
		File artifactFile = new File(artifactVersionPath, Utils.coordsToFileName(artifactId, version, extension));
		FileUtils.writeStringToFile(artifactFile, content);
	}

	private void appendProductList(String groupId, String artifactId, String version, String extension)
			throws IOException, FileNotFoundException, MalformedURLException {
		List<String> products;
		try (FileInputStream is = new FileInputStream(productListFile)) {
			products = Utils.readLines(is);
		}
		
		if (!products.contains(Utils.coordsToString(groupId, artifactId))) {
			products.add(Utils.coordsToString(groupId, artifactId));
		}
		
		FileUtils.writeLines(productListFile, products);
	}

	private void appendMetadata(String groupId, String artifactId, String version, File artifactRoot)
			throws IOException, FileNotFoundException {
		File mavenMetadataFile = new File(artifactRoot, ArtifactoryReader.METADATA_FILE_NAME);
		if (!mavenMetadataFile.exists()) {
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			URL emptyMetadataUrl = cl.getResource(AITestEnvironment.TEST_RESOURCES_PATH + "/empty-maven-metadata.xml");
			FileUtils.copyFile(new File(emptyMetadataUrl.getFile()), mavenMetadataFile);
		}
		
		MetadataXpp3Reader reader = new MetadataXpp3Reader();
		Metadata metaData;
		try (InputStream is = mavenMetadataFile.toURI().toURL().openStream()) {
			metaData = reader.read(is);
			Versioning vers = metaData.getVersioning();
			metaData.setGroupId(groupId);
			metaData.setArtifactId(artifactId);
			List<String> versions = vers.getVersions();
			versions.add(version);
		} catch (IOException | XmlPullParserException e) {
			throw new RuntimeException(e);
		}
		
		try (FileOutputStream os = new FileOutputStream(mavenMetadataFile)) {
			MetadataXpp3Writer writter = new MetadataXpp3Writer();
			writter.write(os, metaData);
		}
	}

	

}

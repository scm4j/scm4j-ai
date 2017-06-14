package org.scm4j.ai;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.exceptions.EProductNotFound;

import com.google.common.io.Files;

public class Repository {
	
	public static final String PRODUCTS_FILE_NAME = "products";
	public static final String REPOS_FILE_NAME = "repos";
	public static final String METADATA_FILE_NAME = "maven-metadata.xml";
	private URL url;
	
	public Repository(String url) {
		try {
			this.url = new URL(Utils.appendSlash(url));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public List<String> getProducts() {
		try (InputStream is = new URL(url, PRODUCTS_FILE_NAME).openStream();
			 InputStreamReader isr = new InputStreamReader(is);
			 BufferedReader reader = new BufferedReader(isr)) {
			
			String line = reader.readLine();
			List<String> res = new ArrayList<>();
			while (line != null) {
				res.add(line);
				line = reader.readLine();
			}
			return res;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public Boolean hasProduct(String productName) {
		return getProducts().contains(productName);
	}
	
	public List<String> getProductVersions(String productName) {
		if (!hasProduct(productName)) {
			throw new EProductNotFound();
		}
		MetadataXpp3Reader reader = new MetadataXpp3Reader();
		try (InputStream is = getProductMetaDataURL(productName).openStream()) {
			Metadata meta = reader.read(is);
			Versioning vers = meta.getVersioning();
			return vers.getVersions();
		} catch (IOException | XmlPullParserException e) {
			throw new RuntimeException(e);
		}
	}

	private URL getProductMetaDataURL(String productName) throws MalformedURLException {
		return new URL(new URL(url, Utils.removeLastSlash(productName) + "/"), METADATA_FILE_NAME);
	}

	public static List<Repository> loadFromWorkingFolder(File workingFolder) {
		List<String> repoUrls;
		File reposFile = new File(workingFolder, REPOS_FILE_NAME);
		if (!reposFile.exists()) {
			throw new ENoConfig("repos file is not found in the working folder");
		}
		try {
			repoUrls = Files.readLines(reposFile, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		List<Repository> res = new ArrayList<>();
		for (String repoUrl : repoUrls) {
			res.add(new Repository(repoUrl));
		}
		return res;
	}

	public String getUrl() {
		return url.toString();
	}
	
	public String getProductUrl(String productName, String version, String extension) {
		return this.url.toString() + Utils.getProductRelativeUrl(productName, version, extension);
	}

	@Override
	public String toString() {
		return url.toString();
	}
	
}
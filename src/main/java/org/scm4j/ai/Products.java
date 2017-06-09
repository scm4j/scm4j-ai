package org.scm4j.ai;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

public class Products {
	
	private final Map<String, String> urls = new HashMap<>();

	public Products(File workingFolder) {
		try (InputStreamReader productsInputStreamReader = new FileReader(new File(workingFolder, "products"))) {
			@SuppressWarnings("unchecked")
			List<String> lines = IOUtils.readLines(productsInputStreamReader);
			for (String line : lines) {
				String[] split = line.split("=");
				urls.put(split[0], split[1]);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public Map<String, String> getUrls() {
		return urls;
	}
	
	public String getProductUrl(String productName, String version, String extension) {
		String url = urls.get(productName) + "/" + version + "/";
		return url + productName + "-" + version +  extension;
	}
	
	public String getMetaDataUrl(String productName) {
		return urls.get(productName) + "/" + "maven-metadata.xml";
	}
}

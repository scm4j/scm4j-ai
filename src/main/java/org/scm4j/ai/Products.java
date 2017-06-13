package org.scm4j.ai;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.google.common.io.Files;

public class Products {
	
	private final Map<String, String> urls = new HashMap<>();
	
	private static List<String> readLines(Reader input) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        List<String> res = new ArrayList<>();
        String line = reader.readLine();
        while (line != null) {
        	res.add(line);
            line = reader.readLine();
        }
        return res;
    }

	public Products(File workingFolder) {
		try (InputStreamReader reposInputStreamReader = new FileReader(new File(workingFolder, "repos"))) {
			List<String> repos = readLines(reposInputStreamReader);
			for (String repo : repos) {
				URL url = new URL(repo);
				InputStream is = url.openStream();
				InputStreamReader isr = new InputStreamReader(is);
				List<String> products = readLines(isr);
				for (String product : products) {
					if (!urls.containsKey(product)) {
						urls.put(product, repo);
					}
				}
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

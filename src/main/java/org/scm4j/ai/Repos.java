package org.scm4j.ai;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;

public class Repos {
	
	private List<String> repos;
	
	@SuppressWarnings("unchecked")
	public Repos(File workingFolder) throws FileNotFoundException {
		File reposFile = new File(workingFolder, "repos");
		if (!reposFile.exists()) {
			throw new FileNotFoundException("repos file not found");
		}
		try (InputStreamReader productsInputStreamReader = new FileReader(reposFile)) {
			repos = IOUtils.readLines(productsInputStreamReader);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public InputStream getContent(String relativeUrl) {
		InputStream res;
		for (String repo : repos) {
			try {
				URL url = new URL(new URL(repo + "/"), relativeUrl);
				res = url.openStream();
				if (res != null) {
					return res;
				}
			} catch (Exception e) {
				continue;
			}
		}
		throw new EArtifactNotFound("resource not found in known repositories: " + relativeUrl);
	}
}

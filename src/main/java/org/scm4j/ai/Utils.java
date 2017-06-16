package org.scm4j.ai;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

public class Utils {
	
	public static String removeLastSlash(String url) {
	    if(url.endsWith("/")) {
	        return url.substring(0, url.lastIndexOf("/"));
	    } else {
	        return url;
	    }
	}

	public static String appendSlash(String url) {
		return removeLastSlash(url) + "/";
	}
	
	public static String getProductRelativePath(String productName, String version, String extension) {
		return Utils.removeLastSlash(productName) + File.separator + version + File.separator 
				+ productName.substring(productName.lastIndexOf(File.separator) + 1, productName.length()) 
				+ "-" + version + extension;
	}
	
	@SuppressWarnings("unchecked")
	public static List<String> readLines(InputStream is) {
		try {
			return IOUtils.readLines(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Map<String, String> readMap(InputStream is) {
		Map<String, String> res = new HashMap<>();
		List<String> lines = readLines(is);
		for (String line : lines) {
			String[] entry = line.split("=");
			res.put(entry[0], entry[1]);
		}
		return res;
	}
	
	public static String coordsToString(String groupId, String artifactId) {
		return groupId + ":" + artifactId;
	}

	public static String coordsToFileName(String artifactId, String version, String extension) {
		return artifactId + "-" + version + extension;
	}

	public static String coordsToFolderStructure(String groupId, String artifactId) {
		return new File(groupId.replace(".", File.separator), artifactId).getPath();
	}
	
	public static String coordsToFolderStructure(String groupId, String artifactId, String version) {
		return new File(coordsToFolderStructure(groupId, artifactId), version).getPath();
	}
	
	public static String coordsToFilePath(String groupId, String artifactId, String version, String extension) {
		return new File(coordsToFolderStructure(groupId, artifactId, version), 
				coordsToFileName(artifactId, version, extension)).getPath();
	}

	public static String productNameToUrlStructure(String productName) {
		return productName.replace(".", "/").replace(":", "/");
	}
}

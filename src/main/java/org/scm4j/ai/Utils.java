package org.scm4j.ai;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.yaml.snakeyaml.Yaml;

public class Utils {
	
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

	public static Map<String,ArrayList<String>> readYml(InputStream is) {
		try {
			Yaml yaml = new Yaml();
			@SuppressWarnings("unchecked")
			Map<String, ArrayList<String>> res = yaml.loadAs(is, HashMap.class);
			is.close();
			return res;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String coordsToString(String groupId, String artifactId, String version, String extension) {
		return coordsToString(groupId, artifactId) + ":" + version + ":" + extension;
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
	
	public static String coordsToRelativeFilePath(String groupId, String artifactId, String version, String extension) {
		return new File(coordsToFolderStructure(groupId, artifactId, version), 
				coordsToFileName(artifactId, version, extension)).getPath();
	}

	public static String coordsToUrlStructure(String groupId, String artifactId) {
		return coordsToString(groupId, artifactId).replace(".", "/").replace(":", "/");
	}
}

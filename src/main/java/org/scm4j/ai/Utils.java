package org.scm4j.ai;

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
	
	public static String getProductRelativeUrl(String productName, String version, String extension) {
		return Utils.removeLastSlash(productName) + "/" + version + "/" 
				+ productName.substring(productName.lastIndexOf("/") + 1, productName.length()) 
				+ "-" + version +  extension;
	}
}

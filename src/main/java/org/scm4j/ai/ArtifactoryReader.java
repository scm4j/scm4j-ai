package org.scm4j.ai;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;

public class ArtifactoryReader {

	public static final String METADATA_FILE_NAME = "maven-metadata.xml";
	public static final String POM_FILE_EXTENTION = ".pom";
	
	private final URL url;
	private final String password;
	private final String userName;
	
	public ArtifactoryReader(String url, String userName, String password) throws Exception {
		this.userName = userName;
		this.password = password;
		this.url = new URL(StringUtils.appendIfMissing(url, "/"));
	}
	
	public String getProductListReleaseVersion() throws Exception {
		try (InputStream is = getContentStream(getProductMetaDataURL(ProductList.PRODUCT_LIST_GROUP_ID,
				ProductList.PRODUCT_LIST_ARTIFACT_ID))) {
			MetadataXpp3Reader reader = new MetadataXpp3Reader();
			Metadata meta = reader.read(is);
			Versioning vers = meta.getVersioning();
			return vers.getRelease();
		} 
	}
	
	public InputStream getContentStream(URL url) throws Exception {
		if (url.getProtocol().equals("file")) {
			return url.openStream();
		} else {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setDoOutput(true);
			con.setRequestMethod("GET");
			con.setRequestProperty("Authorization", "Basic " 
					+ Base64.encodeBase64String((userName + ":" + password).getBytes()));
			return con.getInputStream();
		}
	}
	
	public InputStream getContentStream(String groupId, String artifactId, String version, String extension) throws Exception {
		return getContentStream(getProductUrl(groupId, artifactId, version, extension));
	}

	public URL getProductPomURL(String groupId, String artifactId, String version) throws MalformedURLException {
		return new URL(new URL(url, Utils.coordsToUrlStructure(groupId, artifactId, version) + "/"), Utils.coordsToFileName(artifactId, version, POM_FILE_EXTENTION));
	}

	public URL getProductMetaDataURL(String groupId, String artifactId) throws MalformedURLException {
		return new URL(new URL(url, Utils.coordsToUrlStructure(groupId, artifactId) + "/"), METADATA_FILE_NAME);
	}

	public static ArtifactoryReader getByUrl(String repoUrl) throws Exception {
		URL url = new URL(repoUrl);
		String userInfoStr = url.getUserInfo();
		if (userInfoStr != null) {
			String[] userInfo = userInfoStr.split(":");
			repoUrl = repoUrl.replace(userInfoStr + "@",  "");
			if (userInfo.length == 2) {
				return new ArtifactoryReader(repoUrl, userInfo[0], userInfo[1]);
			} 
		}
		return new ArtifactoryReader(repoUrl, null, null);
	}

	public URL getProductUrl(String groupId, String artifactId, String version, String extension) throws Exception {
		return new URL(this.url, Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension)
				.replace("\\",  "/"));
	}

	@Override
	public String toString() {
		return url.toString();
	}
}
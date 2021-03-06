package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class ArtifactoryReader {

	static final String METADATA_FILE_NAME = "maven-metadata.xml";
	static final String LOCAL_METADATA_FILE_NAME = "maven-metadata-local.xml";

	private final URL url;
	private final String password;
	private final String userName;

	@SneakyThrows
	private ArtifactoryReader(String url, String userName, String password) {
		this.userName = userName;
		this.password = password;
		this.url = new URL(StringUtils.appendIfMissing(url, "/"));
	}

	@SneakyThrows
	static ArtifactoryReader getByUrl(String repoUrl) {
		URL url = new URL(repoUrl);
		String userInfoStr = url.getUserInfo();
		if (userInfoStr != null) {
			String[] userInfo = userInfoStr.split(":");
			repoUrl = repoUrl.replace(userInfoStr + "@", "");
			if (userInfo.length == 2) return new ArtifactoryReader(repoUrl, userInfo[0], userInfo[1]);
		}
		return new ArtifactoryReader(repoUrl, null, null);
	}

	List<String> getProductVersions(String groupIdAndArtifactId) throws IOException {
		MetadataXpp3Reader reader = new MetadataXpp3Reader();
		URL url = getProductMetaDataURL(groupIdAndArtifactId, METADATA_FILE_NAME);
		try {
			return readVersions(url, reader);
		} catch (FileNotFoundException e) {
			url = getProductMetaDataURL(groupIdAndArtifactId, LOCAL_METADATA_FILE_NAME);
			try {
				return readVersions(url, reader);
			} catch (FileNotFoundException e1) {
				return Collections.emptyList();
			}
		}
	}

	private List<String> readVersions(URL url, MetadataXpp3Reader reader) throws IOException {
		try (InputStream is = getContentStream(url)) {
			try {
				Metadata meta = reader.read(is);
				Versioning vers = meta.getVersioning();
				List<String> versions = vers.getVersions();
				versions.addAll(vers.getSnapshotVersions().stream()
						.map(SnapshotVersion::getVersion)
						.collect(Collectors.toList()));
				return versions;
			} catch (XmlPullParserException e) {
				throw new RuntimeException(e);
			}
		}
	}

	String getProductListReleaseVersion() throws IOException {
		try {
			@Cleanup
			InputStream is = getContentStream(getProductMetaDataURL(ProductList.PRODUCT_LIST_GROUP_ID + ":"
					+ ProductList.PRODUCT_LIST_ARTIFACT_ID, METADATA_FILE_NAME));
			MetadataXpp3Reader reader = new MetadataXpp3Reader();
			Metadata meta = reader.read(is);
			Versioning vers = meta.getVersioning();
			return vers.getRelease();
		} catch (XmlPullParserException e) {
			throw new RuntimeException(e);
		}
	}

	private InputStream getContentStream(URL url) throws IOException {
		if (url.getProtocol().equals("file")) {
			return url.openStream();
		} else {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setDoOutput(true);
			con.setRequestMethod("GET");
			if (userName != null && password != null)
				con.setRequestProperty("Authorization", "Basic "
						+ Base64.encodeBase64String((userName + ":" + password).getBytes()));
			if (con.getResponseCode() == 401)
				throw new FileNotFoundException();
			return con.getInputStream();
		}
	}

	@SneakyThrows
	private URL getProductMetaDataURL(String groupIdAndArtifactId, String metadataName) {
		String[] arr = groupIdAndArtifactId.split(":");
		String groupId = arr[0].replace('.', '/');
		return new URL(new URL(this.url, groupId + "/" + arr[1] + "/"),
				metadataName);
	}

	@Override
	public String toString() {
		return url.toString();
	}
}
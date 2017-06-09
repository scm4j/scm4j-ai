package org.scm4j.ai;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class AIRunner {

	private Products products;
	private Repos repos;
	private File repository;

	public AIRunner(File workingFolder) throws FileNotFoundException {
		repository = new File(workingFolder, "repository");
		try {
			if (!repository.exists()) {
				Files.createDirectory(repository.toPath());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		products = new Products(workingFolder);
		repos = new Repos(workingFolder);
	}

	public List<String> listVersions(String productName) {
		try {
			MetadataXpp3Reader reader = new MetadataXpp3Reader();
			InputStream is = repos.getContent(products.getMetaDataUrl(productName));
			Metadata meta = reader.read(is);
			Versioning vers = meta.getVersioning();
			return vers.getVersions();
		} catch (IOException | XmlPullParserException e) {
			throw new RuntimeException(e);
		}
	}

	public File download(String productName, String version, String extension) {
		String fileUrlSr = products.getProductUrl(productName, version, extension);
		File res = new File(repository, fileUrlSr);
		if (res.exists()) {
			return res;
		}
		try {
			File parent = res.getParentFile();
			if (!parent.exists()) {
				parent.mkdirs();
			}

			res.createNewFile();

			try (FileOutputStream out = new FileOutputStream(res)) {
				InputStream in = repos.getContent(fileUrlSr);
				IOUtils.copy(in, out);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return res;
	}
}

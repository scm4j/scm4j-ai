package org.scm4j.ai;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AIRunnerTest {

	private static final String TEST_PRODUCT_NAME = "guava";
	private static final String TEST_ENVIRONMENT_PATH = "org/scm4j/ai/TestEnvironment";
	private static final String TEST_ARTIFACTORY_PATH = "org/scm4j/ai/RemoteArtifactory";

	private File workingFolder;
	private File artifactoryFolder;
	private File reposFile;

	private AIRunner runner;
	private ClassLoader cl;

	private File getResourceFolder(String path) throws IOException {
		final URL url = cl.getResource(path);
		return new File(url.getFile());
	}

	@Before
	public void setUp() throws IOException {
		cl = Thread.currentThread().getContextClassLoader();
		createEnvironment();
		createArtifactory();
		createReposFile();
		runner = new AIRunner(workingFolder);
	}

	private void createArtifactory() throws IOException {
		artifactoryFolder = Files
				.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(), "scm4j-ai-test-art-")
				.toFile();
		FileUtils.copyDirectory(getResourceFolder(TEST_ARTIFACTORY_PATH), artifactoryFolder);

	}

	private void createEnvironment() throws IOException {
		workingFolder = Files
				.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(), "scm4j-ai-test-env-")
				.toFile();
		FileUtils.copyDirectory(getResourceFolder(TEST_ENVIRONMENT_PATH), workingFolder);
	}

	private void createReposFile() throws IOException {
		reposFile = new File(workingFolder, "repos");
		reposFile.createNewFile();
		FileUtils.writeLines(reposFile, Arrays.asList("file:///" + artifactoryFolder.getPath().replace("\\", "/")));
	}

	@After
	public void tearDown() {
		workingFolder.delete();
	}

	@Test(expected = FileNotFoundException.class)
	public void testNoReposNoWork() throws FileNotFoundException {
		reposFile.delete();
		new AIRunner(workingFolder);
	}

	@Test
	public void testListVersions() throws Exception {
		List<String> vers = runner.listVersions(TEST_PRODUCT_NAME);
		assertNotNull(vers);
		assertTrue(vers.contains("22.0"));
		assertTrue(vers.contains("20.0"));
	}

	@Test
	public void testDownload() throws IOException {
		File artifact = runner.download(TEST_PRODUCT_NAME, "20.0", ".jar");
		assertTrue(artifact.exists());
		String relativeArtifactPath = artifact.getPath().replace(workingFolder.getPath() + "\\repository", "");
		FileUtils.contentEquals(artifact, new File(artifactoryFolder, relativeArtifactPath));
	}

	@Test(expected = EArtifactNotFound.class)
	public void testUnknownRepo() throws IOException {
		FileUtils.writeLines(reposFile, Arrays.asList("file:///c:/unexisting/artifactory/sdfsdff"));
		AIRunner runner = new AIRunner(workingFolder);
		runner.download(TEST_PRODUCT_NAME, "20.0", ".jar");
	}

	@Test
	public void testListProducts() throws IOException {
		File productsFile = new File(workingFolder, "products");
		productsFile.createNewFile();
		FileUtils.writeLines(productsFile, Arrays.asList("unTill\r\nUBL\r\nunTillDb"));
		AIRunner runner = new AIRunner(workingFolder);
		List<String> products = runner.listProducts(TEST_ARTIFACTORY_PATH);
		assertNotNull(products);
		assertTrue(products.containsAll(Arrays.asList("unTill", "UBL", "unTilDb")));
	}

}

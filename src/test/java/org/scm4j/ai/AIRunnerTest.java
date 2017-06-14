package org.scm4j.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.installers.IInstaller;
import org.scm4j.ai.installers.InstallerFactory;

public class AIRunnerTest {

	private static final String TEST_PRODUCT_GUAVA = "com/google/guava/guava";
	private static final String TEST_PRODUCT_UBL = "eu/untill/UBL";

	private AITestEnvironment env;
	private AIRunner runner;

	@After
	public void tearDown() throws IOException {
		FileUtils.deleteDirectory(env.getBaseTestFolder());
	}

	@Before
	public void setUp() throws IOException {
		env = new AITestEnvironment();
		env.prepareEnvironment();
		runner = new AIRunner(env.getEnvFolder());
	}

	@Test
	public void testNoReposNoWork() throws FileNotFoundException {
		env.getReposFile().delete();
		try {
			new AIRunner(env.getEnvFolder());
			fail();
		} catch (ENoConfig e) {

		}
	}

	@Test
	public void testLoadRepos() {
		List<Repository> repos = runner.getRepos();
		assertNotNull(repos);
		repos.containsAll(Arrays.asList(
				Utils.appendSlash(env.getArtifactory1Url()),
				Utils.appendSlash(env.getArtifactory2Url())));
	}

	@Test
	public void testListVersions() throws Exception {
		List<String> vers = runner.listVersions(TEST_PRODUCT_GUAVA);
		assertNotNull(vers);
		assertTrue(vers.containsAll(Arrays.asList("22.0", "20.0", "24.0-rc1", "25.0")));
	}

	@Test
	public void testDownloadFromArtifactory1() throws Exception {
		AIRunner mockedRunner = Mockito.spy(new AIRunner(env.getEnvFolder()));
		File artifact = mockedRunner.download(TEST_PRODUCT_GUAVA, "20.0", ".jar");
		assertTrue(artifact.exists());
		String relativeArtifactPath = artifact.getPath().replace(env.getEnvFolder().getPath() + "\\repository", "");
		FileUtils.contentEquals(artifact, new File(env.getArtifactory1Folder(), relativeArtifactPath));
		assertTrue(artifact.getPath(), artifact.getPath().endsWith("repository\\com\\google\\guava\\guava\\20.0\\guava-20.0.jar"));

		// no download second time
		mockedRunner.download(TEST_PRODUCT_GUAVA, "20.0", ".jar");
		Mockito.verify(mockedRunner, Mockito.times(1)).getContent("file://localhost/"
				+ new File(env.getArtifactory1Folder(), Utils.getProductRelativeUrl(TEST_PRODUCT_GUAVA, "20.0", ".jar"))
						.getPath().replace("\\", "/"));
	}

	@Test
	public void testDownloadFromArtifactory2() throws IOException {
		File artifact = runner.download(TEST_PRODUCT_GUAVA, "25.0", ".jar");
		assertTrue(artifact.exists());
		String relativeArtifactPath = artifact.getPath().replace(env.getEnvFolder().getPath() + "\\repository", "");
		FileUtils.contentEquals(artifact, new File(env.getArtifactory1Folder(), relativeArtifactPath));
		assertTrue(artifact.getPath(), artifact.getPath().endsWith("repository\\com\\google\\guava\\guava\\25.0\\guava-25.0.jar"));

		artifact = runner.download(TEST_PRODUCT_UBL, "18.5", ".jar");
		assertTrue(artifact.exists());
		relativeArtifactPath = artifact.getPath().replace(env.getEnvFolder().getPath() + "\\repository", "");
		FileUtils.contentEquals(artifact, new File(env.getArtifactory2Folder(), relativeArtifactPath));
		assertTrue(artifact.getPath().endsWith("repository\\eu\\untill\\UBL\\18.5\\UBL-18.5.jar"));
	}

	@Test
	public void testDownloadUnknownArtifact() throws IOException {
		try {
			runner.download("unknown artifact", "20.0", ".jar");
			fail();
		} catch (EArtifactNotFound e) {
		}
	}

	@Test
	public void testListProducts() throws IOException {
		List<String> products = runner.listProducts();
		assertNotNull(products);
		assertTrue(products.contains(TEST_PRODUCT_GUAVA));
		assertTrue(products.contains(TEST_PRODUCT_UBL));
		assertTrue(products.size() == 2);
	}

	@Test
	public void testUrls() throws Exception {
		assertEquals(Utils.getProductRelativeUrl("guava", "20.0", ".jar"), "guava/20.0/guava-20.0.jar");
		assertEquals(Utils.getProductRelativeUrl("com/google/guava/guava", "20.0", ".jar"),
				"com/google/guava/guava/20.0/guava-20.0.jar");
		
		Repository repo = new Repository(env.getArtifactory1Url());
		assertEquals(repo.getProductUrl("guava", "20.0", ".jar"),
				Utils.appendSlash(env.getArtifactory1Url()) + "guava/20.0/guava-20.0.jar");
	}

	@Test
	public void testDownloadAndInstall() {
		File product = runner.download(TEST_PRODUCT_GUAVA, "20.0", ".jar");
		InstallerFactory iFac = Mockito.mock(InstallerFactory.class);
		IInstaller installer = Mockito.mock(IInstaller.class);
		Mockito.doReturn(installer).when(iFac).getInstaller(product);
		runner.setInstallerFactory(iFac);
		runner.install(product);
		Mockito.verify(installer).install();
	}

}

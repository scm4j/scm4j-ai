package org.scm4j.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
	private static final String TEST_ARTIFACTORY_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test")
			.getPath();

	private AITestEnvironment env;
	
	private String ublArtifactId = "UBL-" + UUID.randomUUID().toString();
	private String guavaArtifactId = "guava-" + UUID.randomUUID().toString();
	
	@Before
	public void setUp() throws IOException {
		FileUtils.deleteDirectory(new File(TEST_ARTIFACTORY_DIR));
		env = new AITestEnvironment();
		env.prepareEnvironment();
		
		ArtifactoryWriter aw = new ArtifactoryWriter(env.getArtifactory1Folder());
		registerArtifactory(env.getArtifactory1Folder());
		aw.install("eu.untill", ublArtifactId, "18.5", ".jar", "ubl 18.5 artifact content");
		
		aw = new ArtifactoryWriter(env.getArtifactory2Folder());
		registerArtifactory(env.getArtifactory2Folder());
		aw.install("eu.untill", ublArtifactId, "18.0", ".jar", "ubl 18.5 artifact content");
		aw.install("com.google.guava", guavaArtifactId, "20.0-rc1", ".jar", "guava 20.0-rc1 artifact content");
		aw.install("com.google.guava", guavaArtifactId, "21.0", ".jar", "guava 21.0 artifact content");
	}
	
	@After
	public void tearDown() throws IOException {
		FileUtils.deleteDirectory(new File(TEST_ARTIFACTORY_DIR));
		
	}
	
	@Test
	public void testGetProducts() throws IOException {
		AIRunner runner = new AIRunner(env.getEnvFolder());
		List<String> products = runner.listProducts();
		assertNotNull(products);
		assertTrue(products.containsAll(Arrays.asList(
				"eu.untill:" + ublArtifactId, "com.google.guava:" + guavaArtifactId)));
		assertTrue(products.size() == 2);
	}
	
	@Test 
	public void testGetVersions() {
		AIRunner runner = new AIRunner(env.getEnvFolder());
		List<String> versions = runner.listVersions("eu.untill:" + ublArtifactId);
		assertNotNull(versions);
		assertTrue(versions.containsAll(Arrays.asList(
				"18.5", "18.0")));
		assertTrue(versions.size() == 2);
	}
	
	private void registerArtifactory(File artifactoryFolder) throws IOException {
		try (BufferedWriter output = new BufferedWriter(new FileWriter(env.getProductListsFile(), true))) {
			output.append(artifactoryFolder.toURI().toURL().toString() + "\r\n");
		}
	}

	@Test
	public void testListVersions() throws Exception {
		// create repository writer, create and fill test repos by writers. Also write update local repos file by writer.
		// 2 artifactory: 1 - products list, has artifact names and its urls. Artifacts themselves are located on artifactory 2
		
		/*
			String a1Coords = productCoords("a1", ); // UUID.generateUUUID().soString() + ":p1:1.1" 
			String a1Content = UUID.generateUUUID().soString()
			
			rw.install(a1Coords, a1Content);
			
			
			
		 */
		
		List<String> vers = runner.listVersions(TEST_PRODUCT_GUAVA);
		assertNotNull(vers);
		assertTrue(vers.containsAll(Arrays.asList("22.0", "20.0", "24.0-rc1", "25.0")));
	}

	@Test
	public void testNoReposNoWork() throws FileNotFoundException {
		env.getProductListsFile().delete();
		try {
			new AIRunner(env.getEnvFolder());
			fail();
		} catch (ENoConfig e) {

		}
	}

	@Test
	public void testDownloadFromArtifactory1() throws Exception {
		AIRunner mockedRunner = Mockito.spy(new AIRunner(env.getEnvFolder()));
		File artifact = mockedRunner.download(TEST_PRODUCT_GUAVA, "20.0", ".jar");
		assertTrue(artifact.exists());
		String relativeArtifactPath = artifact.getPath().replace(env.getEnvFolder().getPath() + File.separator + "repository", "");
		FileUtils.contentEquals(artifact, new File(env.getArtifactory1Folder(), relativeArtifactPath));
		String ethalon = "repository\\com\\google\\guava\\guava\\20.0\\guava-20.0.jar"
				.replace("\\", File.separator);
		assertTrue(artifact.getPath() + ";" + ethalon, artifact.getPath().endsWith(ethalon));
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
		String relativeArtifactPath = artifact.getPath().replace(env.getEnvFolder().getPath() + File.separator + "repository", "");
		FileUtils.contentEquals(artifact, new File(env.getArtifactory1Folder(), relativeArtifactPath));
		String ethalon = "repository\\com\\google\\guava\\guava\\25.0\\guava-25.0.jar"
				.replace("\\", File.separator);
		assertTrue(artifact.getPath() + ";" + ethalon, artifact.getPath().endsWith(ethalon));

		artifact = runner.download(TEST_PRODUCT_UBL, "18.5", ".jar");
		assertTrue(artifact.exists());
		relativeArtifactPath = artifact.getPath().replace(env.getEnvFolder().getPath() + "\\repository", "");
		FileUtils.contentEquals(artifact, new File(env.getArtifactory2Folder(), relativeArtifactPath));
		assertTrue(artifact.getPath().endsWith("repository\\eu\\untill\\UBL\\18.5\\UBL-18.5.jar".replace("\\", File.separator)));
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
	public void testUrls() throws Exception {
		assertEquals(Utils.getProductRelativeUrl("guava", "20.0", ".jar"), "guava/20.0/guava-20.0.jar");
		assertEquals(Utils.getProductRelativeUrl("com/google/guava/guava", "20.0", ".jar"),
				"com/google/guava/guava/20.0/guava-20.0.jar");
		
		ArtifactoryReader repo = new ArtifactoryReader(env.getArtifactory1Url());
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
	
	@Test
	public void testLoadRepos() {
		List<ArtifactoryReader> repos = runner.getRepos();
		assertNotNull(repos);
		repos.containsAll(Arrays.asList(
				Utils.appendSlash(env.getArtifactory1Url()),
				Utils.appendSlash(env.getArtifactory2Url())));
	}

}

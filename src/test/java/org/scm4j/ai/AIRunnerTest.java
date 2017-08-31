package org.scm4j.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.exceptions.EProductNotFound;
import org.scm4j.ai.installers.IInstaller;
import org.scm4j.ai.installers.InstallerFactory;

public class AIRunnerTest {

	private static final String TEST_UBL_18_0_CONTENT = "ubl 18.0 artifact content";
	private static final String TEST_GUAVA_21_0_CONTENT = "guava 21.0 artifact content";
	private static final String TEST_GUAVA_GROUP_ID = "com.google.guava";
	private static final String TEST_UBL_GROUP_ID = "eu.untill";
	private static final String TEST_ARTIFACTORY_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test")
			.getPath();

	private AITestEnvironment env;
	
	private String ublArtifactId = "UBL-" + UUID.randomUUID().toString();
	private String guavaArtifactId = "guava-" + UUID.randomUUID().toString();
	private String untillId = "unTILL";
	
	@After
	public void tearDown() throws IOException {
		FileUtils.deleteDirectory(new File(TEST_ARTIFACTORY_DIR));
	}
	
	@Before
	public void setUp() throws IOException {
		FileUtils.deleteDirectory(new File(TEST_ARTIFACTORY_DIR));
		env = new AITestEnvironment();
		env.prepareEnvironment();
		
		ArtifactoryWriter aw = new ArtifactoryWriter(env.getArtifactory1Folder());
		appendProductLists(env.getArtifactory1Folder());
		aw.installArtifact(TEST_UBL_GROUP_ID, untillId, "123.4", ".yml", "", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UBL_GROUP_ID, ublArtifactId, "18.5", ".jar", "ubl 18.5 artifact content", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UBL_GROUP_ID, ublArtifactId, "18.0", ".jar", TEST_UBL_18_0_CONTENT, env.getArtifactory1Folder());
		aw = new ArtifactoryWriter(env.getArtifactory2Folder());
		appendProductLists(env.getArtifactory2Folder());
		aw.installArtifact(TEST_GUAVA_GROUP_ID, guavaArtifactId, "20.0-rc1", ".jar", "guava 20.0-rc1 artifact content", env.getArtifactory2Folder());
		aw.installArtifact(TEST_GUAVA_GROUP_ID, guavaArtifactId, "21.0", ".jar", TEST_GUAVA_21_0_CONTENT, env.getArtifactory2Folder());
		
		//aw.installArtifact("eu.untill", "untill", )
		/**
		 * Few Product Component Artifact installed
		 * Now create Product Artifact mdeps wich is a .txt with set of Product Component Artifacts 
		 * and pom.xml which will contain flat artifact list
		 */
		
	}

	//TODO testGetProductsFromYml
	//TODO testDownloadDependenciesFromPom
	
	@Test
	public void testGetProducts() throws IOException {
		AIRunner runner = new AIRunner(env.getEnvFolder());
		List<String> products = runner.listProducts();
		assertNotNull(products);
		assertTrue(products.containsAll(Arrays.asList(
				"eu.untill:" + ublArtifactId, "com.google.guava:" + guavaArtifactId)));
		assertTrue(products.size() == 3);
	}
	
	@Test 
	public void testGetVersions() {
		AIRunner runner = new AIRunner(env.getEnvFolder());
		List<String> versions = runner.listVersions(TEST_UBL_GROUP_ID, ublArtifactId);
		assertNotNull(versions);
		assertTrue(versions.containsAll(Arrays.asList(
				"18.5", "18.0")));
		assertTrue(versions.size() == 2);
		
		versions = runner.listVersions(TEST_GUAVA_GROUP_ID, guavaArtifactId);
		assertNotNull(versions);
		assertTrue(versions.containsAll(Arrays.asList(
				"21.0", "20.0-rc1")));
		assertTrue(versions.size() == 2);
	}
	
	private void appendProductLists(File productListArtifactoryFolder) throws IOException {
		try (BufferedWriter output = new BufferedWriter(new FileWriter(env.getProductListsFile(), true))) {
			output.append(productListArtifactoryFolder.toURI().toURL().toString() + "\r\n");
		}
	}

	@Test
	public void testNoReposNoWork() throws FileNotFoundException {
		env.getProductListsFile().delete();
		assertFalse(env.getProductListsFile().exists());
		try {
			new AIRunner(env.getEnvFolder());
			fail();
		} catch (ENoConfig e) {

		}
	}

	@Test
	public void testDownloadFromArtifactory2() throws Exception {
		AIRunner mockedRunner = Mockito.spy(new AIRunner(env.getEnvFolder()));
		ArtifactoryReader mockedReader1 = Mockito.spy(new ArtifactoryReader(env.getArtifactory1Url(), null, null));
		ArtifactoryReader mockedReader2 = Mockito.spy(new ArtifactoryReader(env.getArtifactory2Url(), null, null));
		mockedRunner.setRepos(Arrays.asList(mockedReader1,mockedReader2));

		File artifact = mockedRunner.get(TEST_GUAVA_GROUP_ID, guavaArtifactId, "21.0", ".jar");
		assertTrue(artifact.exists());
		assertEquals(FileUtils.readFileToString(artifact), TEST_GUAVA_21_0_CONTENT);
		String ethalon = String.format("\\repository\\com\\google\\guava\\%s\\21.0\\%s-21.0.jar", 
				guavaArtifactId, guavaArtifactId).replace("\\", File.separator);
		assertEquals(StringUtils.removeEnd(artifact.getPath(), ethalon), env.getEnvFolder().getPath());
		
		// no download second time
		artifact = mockedRunner.get(TEST_GUAVA_GROUP_ID, guavaArtifactId, "21.0", ".jar");
		assertTrue(artifact.exists());
		Mockito.verify(mockedReader2, Mockito.times(1))
				.getContentStream(TEST_GUAVA_GROUP_ID, guavaArtifactId, "21.0", ".jar");
		Mockito.verify(mockedReader1, Mockito.never())
				.getContentStream(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
	}

	@Test
	public void testDownloadFromArtifactory1() throws Exception {
		AIRunner mockedRunner = Mockito.spy(new AIRunner(env.getEnvFolder()));
		ArtifactoryReader mockedReader1 = Mockito.spy(new ArtifactoryReader(env.getArtifactory1Url(), null, null));
		ArtifactoryReader mockedReader2 = Mockito.spy(new ArtifactoryReader(env.getArtifactory2Url(), null, null));
		mockedRunner.setRepos(Arrays.asList(mockedReader1,mockedReader2));
		
		File artifact = mockedRunner.get(TEST_UBL_GROUP_ID, ublArtifactId, "18.0", ".jar");
		assertTrue(artifact.exists());
		assertEquals(FileUtils.readFileToString(artifact), TEST_UBL_18_0_CONTENT);
		String ethalon = String.format("\\repository\\eu\\untill\\%s\\18.0\\%s-18.0.jar", ublArtifactId, ublArtifactId)
				.replace("\\", File.separator);
		assertEquals(StringUtils.removeEnd(artifact.getPath(), ethalon), env.getEnvFolder().getPath());

		// no download second time
		artifact = mockedRunner.get(TEST_UBL_GROUP_ID, ublArtifactId, "18.0", ".jar");
		assertTrue(artifact.exists());
		Mockito.verify(mockedReader1, Mockito.times(1))
				.getContentStream(TEST_UBL_GROUP_ID, ublArtifactId, "18.0", ".jar");
		Mockito.verify(mockedReader2, Mockito.never())
				.getContentStream(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
	}

	@Test
	public void testUnknownArtifact() throws IOException {
		AIRunner runner = new AIRunner(env.getEnvFolder());
		try {
			runner.listVersions("eu.untill", "unknown artifact");
			fail();
		} catch (EProductNotFound e) {
		}
		
		try {
			runner.get("eu.untill", "unknown artifact", "version", "extension");
			fail();
		} catch (EArtifactNotFound e) {
		}
	}
	
	@Test
	public void testUnknownVersion() {
		AIRunner runner = new AIRunner(env.getEnvFolder());
		try {
			runner.get(TEST_UBL_GROUP_ID, ublArtifactId, "unknown version", ".jar");
			fail();
		} catch (EArtifactNotFound e) {
		}
	}

	@Test
	public void testUrls() throws Exception {
		assertEquals(Utils.coordsToRelativeFilePath("", "guava", "20.0", ".jar"), 
				new File("/guava/20.0/guava-20.0.jar").getPath());
		assertEquals(Utils.coordsToRelativeFilePath("com.google.guava", "guava", "20.0", ".jar"),
				new File("com/google/guava/guava/20.0/guava-20.0.jar").getPath());
		
		ArtifactoryReader repo = new ArtifactoryReader(env.getArtifactory1Url(), null, null);
		assertEquals(repo.getProductUrl("com.google.guava", "guava", "20.0", ".jar"),
				new URL(env.getArtifactory1Folder().toURI().toURL(), "com/google/guava/guava/20.0/guava-20.0.jar"));
	}

	@Test
	public void testDownloadAndInstall() {
		AIRunner runner = new AIRunner(env.getEnvFolder());
		File product = runner.get(TEST_GUAVA_GROUP_ID, guavaArtifactId, "21.0", ".jar");
		InstallerFactory iFac = Mockito.mock(InstallerFactory.class);
		IInstaller installer = Mockito.mock(IInstaller.class);
		Mockito.doReturn(installer).when(iFac).getInstaller(product);
		runner.setInstallerFactory(iFac);
		runner.install(product);
		Mockito.verify(installer).install();
	}
	
	@Test
	public void testLoadRepos() {
		AIRunner runner = new AIRunner(env.getEnvFolder());
		List<ArtifactoryReader> repos = runner.getRepos();
		assertNotNull(repos);
		repos.containsAll(Arrays.asList(
				StringUtils.appendIfMissing(env.getArtifactory1Url(), "/"),
				StringUtils.appendIfMissing(env.getArtifactory2Url(), "/")));
	}

}

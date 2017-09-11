package org.scm4j.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.exceptions.EProductNotFound;
import org.scm4j.ai.installers.IInstaller;
import org.scm4j.ai.installers.InstallerFactory;

import javax.naming.MalformedLinkException;

public class AIRunnerTest {

	private static final String TEST_UBL_22_2_CONTENT = "ubl 22.2 artifact content";
	private static final String TEST_DEP_CONTENT = "dependency content";
	private static final String TEST_UNTILL_GROUP_ID = "eu.untill";
	private static final String TEST_JOOQ_GROUP_ID = "org.jooq";
	private static final String TEST_AXIS_GROUP_ID = "org.apache.axis";
	private static final String TEST_ARTIFACTORY_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test")
			.getPath();
	private static final String TEST_GUAVA_21_0_CONTENT = "guava 21.0 artifact content";
	private static final String TEST_GUAVA_GROUP_ID = "com.google.guava";

	private AITestEnvironment env;
	
	private String ublArtifactId = "UBL";
	private String untillArtifactId = "unTILL";
	private String jooqArtifact = "jooq";
	private String axisArtifact = "axis";
	private String axisJaxrpcArtifact = "axis-jaxrpc";
	private String guavaArtifactId = "guava";
	
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
		aw.generateProductListArtifact();
		File pathToUntill = new File(env.getArtifactory1Folder(),Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", ".yml" ));
		aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", ".yml", "", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "124.5", ".yml", "", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UNTILL_GROUP_ID, ublArtifactId, "22.2",".jar",TEST_UBL_22_2_CONTENT, pathToUntill);
		aw.installArtifact(TEST_JOOQ_GROUP_ID, jooqArtifact, "3.1.0", ".jar", TEST_DEP_CONTENT, pathToUntill);
		aw = new ArtifactoryWriter(env.getArtifactory2Folder());
		aw.installArtifact(TEST_AXIS_GROUP_ID, axisArtifact, "1.4",".jar", TEST_DEP_CONTENT, pathToUntill);
		aw.installArtifact(TEST_AXIS_GROUP_ID, axisJaxrpcArtifact,"1.4",".jar", TEST_DEP_CONTENT, pathToUntill);
	}
	
	@Test
	public void testGetProducts() throws IOException {
		AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
		Set<String> products = runner.getProductList().getProducts();
		assertNotNull(products);
		assertTrue(products.containsAll(Arrays.asList(
				"eu.untill:unTILL")));
		assertTrue(products.size() == 1);
	}
	
	@Test 
	public void testGetVersions() throws Exception {
		AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
		List<String> versions = runner.getProductVersions(TEST_UNTILL_GROUP_ID, untillArtifactId);
		assertNotNull(versions);
		assertTrue(versions.containsAll(Arrays.asList(
				"123.4", "124.5")));
		assertTrue(versions.size() == 2);
	}

	@Test
	public void testNoReposNoWork() throws FileNotFoundException {
		try {
			new AIRunner(env.getEnvFolder(), "random URL", null, null);
			fail();
		} catch (Exception e) {

		}
	}

	@Ignore
	public void testDownloadFromArtifactory2() throws Exception {
		AIRunner mockedRunner = Mockito.spy(new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null));
		ArtifactoryReader mockedReader1 = Mockito.spy(new ArtifactoryReader(env.getArtifactory1Url(), null, null));
		ArtifactoryReader mockedReader2 = Mockito.spy(new ArtifactoryReader(env.getArtifactory2Url(), null, null));

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

	@Ignore
	public void testDownloadFromArtifactory1() throws Exception {
		AIRunner mockedRunner = Mockito.spy(new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null));
		ArtifactoryReader mockedReader1 = Mockito.spy(new ArtifactoryReader(env.getArtifactory1Url(), null, null));
		ArtifactoryReader mockedReader2 = Mockito.spy(new ArtifactoryReader(env.getArtifactory2Url(), null, null));
		
		File artifact = mockedRunner.get(TEST_UNTILL_GROUP_ID, ublArtifactId, "22.2", ".jar");
		assertTrue(artifact.exists());
		assertEquals(FileUtils.readFileToString(artifact), TEST_UBL_22_2_CONTENT);
		String ethalon = String.format("\\repository\\eu\\untill\\%s\\22.2\\%s-22.2.jar", ublArtifactId, ublArtifactId)
				.replace("\\", File.separator);
		assertEquals(StringUtils.removeEnd(artifact.getPath(), ethalon), env.getEnvFolder().getPath());

		// no download second time
		artifact = mockedRunner.get(TEST_UNTILL_GROUP_ID, ublArtifactId, "22.2", ".jar");
		assertTrue(artifact.exists());
		Mockito.verify(mockedReader1, Mockito.times(1))
				.getContentStream(TEST_UNTILL_GROUP_ID, ublArtifactId, "22.2", ".jar");
		Mockito.verify(mockedReader2, Mockito.never())
				.getContentStream(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
	}

	@Test
	public void testUnknownArtifact() throws Exception {
		AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
		try {
			runner.getProductVersions("eu.untill", "unknown artifact");
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
		AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
		try {
			runner.get(TEST_UNTILL_GROUP_ID, ublArtifactId, "unknown version", ".jar");
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
		URL url = new URL(env.getArtifactory1Folder().toURI().toURL(), "com/google/guava/guava/20.0/guava-20.0.jar");
		URL expectedURL = repo.getProductUrl("com.google.guava", "guava", "20.0", ".jar");
		assertEquals(expectedURL, url);
	}

	@Ignore
	public void testDownloadAndInstall() {
		AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
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
		AIRunner runner = new AIRunner(env.getEnvFolder(),env.getArtifactory1Url(), null, null);
		List<ArtifactoryReader> repos = runner.getProductList().getRepos();
		assertNotNull(repos);
		repos.containsAll(Arrays.asList(
				StringUtils.appendIfMissing(env.getArtifactory1Url(), "/"),
				StringUtils.appendIfMissing(env.getArtifactory2Url(), "/")));
	}

	@Test
	public void testGetComponents() throws Exception {
		AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
		List<Artifact> artifacts = runner.getComponents(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4");
		assertFalse(artifacts.isEmpty());
		assertTrue(artifacts.size() == 1);
	}

	@Test
	public void testDownloadComponents() throws Exception {
		AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
		List<Artifact> artifacts = runner.getComponents(TEST_UNTILL_GROUP_ID,untillArtifactId,"123.4");
		runner.downloadComponents(artifacts);
		File repository = new File(env.getEnvFolder(), "repository");
		File artifact1 = new File(repository,Utils.coordsToRelativeFilePath(TEST_JOOQ_GROUP_ID,jooqArtifact,"3.1.0",".jar"));
		File pom = new File(repository, Utils.coordsToRelativeFilePath(TEST_AXIS_GROUP_ID,axisArtifact,"1.4",".pom"));
		assertEquals(FileUtils.readFileToString(artifact1),TEST_DEP_CONTENT);
		assertNotNull(FileUtils.readFileToString(pom));
	}

	//TODO test local repo for deps and other stuff
	@Test
	public void testDownloadProduct() throws Exception {
		AIRunner runner = new AIRunner(env.getEnvFolder(), env. getArtifactory1Url(), null, null);
		File product = runner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", ".yml");
		assertNotNull(FileUtils.readFileToString(product));
	}
}

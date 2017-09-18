package org.scm4j.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.google.common.base.Utf8;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.CharSet;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.junit.*;
import org.mockito.Mockito;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.EProductNotFound;
import org.scm4j.ai.api.IInstaller;
import org.scm4j.ai.installers.InstallerFactory;
import org.scm4j.ai.installers.UnzipArtifact;

public class AIRunnerTest {

	private static final String TEST_UBL_22_2_CONTENT = "ubl 22.2 artifact content";
	private static final String TEST_DEP_CONTENT = "dependency content";
	private static final String TEST_UNTILL_GROUP_ID = "eu.untill";
	private static final String TEST_JOOQ_GROUP_ID = "org.jooq";
	private static final String TEST_AXIS_GROUP_ID = "org.apache.axis";
	private static final String TEST_ARTIFACTORY_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test")
			.getPath();

	private AITestEnvironment env = new AITestEnvironment();
	
	private String ublArtifactId = "UBL";
	private String untillArtifactId = "unTILL";
	private String jooqArtifact = "jooq";
	private String axisArtifact = "axis";
	private String axisJaxrpcArtifact = "axis-jaxrpc";
	private File pathToUntill;

	@After
	public void after() throws IOException {
		FileUtils.deleteDirectory(new File(TEST_ARTIFACTORY_DIR));
	}

	@Before
	public void setUp() throws IOException {
		env.prepareEnvironment();
		env.createEnvironment();
		ArtifactoryWriter aw = new ArtifactoryWriter(env.getArtifactory1Folder());
		aw.generateProductListArtifact();
		aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", ".jar",
				"ProductStructureDataLoader", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "124.5", ".jar",
				"ProductStructureDataLoader", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UNTILL_GROUP_ID, ublArtifactId, "22.2",".war",
				TEST_UBL_22_2_CONTENT, env.getArtifactory1Folder());
		aw.installArtifact(TEST_JOOQ_GROUP_ID, jooqArtifact, "3.1.0", ".jar",
				TEST_DEP_CONTENT, env.getArtifactory1Folder());
		aw = new ArtifactoryWriter(env.getArtifactory2Folder());
		aw.installArtifact(TEST_AXIS_GROUP_ID, axisArtifact, "1.4",".jar",
				TEST_DEP_CONTENT, env.getArtifactory2Folder());
		aw.installArtifact(TEST_AXIS_GROUP_ID, axisJaxrpcArtifact,"1.4",".jar",
				TEST_DEP_CONTENT, env.getArtifactory2Folder());
		pathToUntill = new File(env.getArtifactory1Folder(), Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID,
				untillArtifactId, "123.4", ".jar"));
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
	public void testDownloadAndDeployProduct() throws Exception {
		AIRunner runner = new AIRunner(env.getEnvFolder(), env. getArtifactory1Url(), null, null);
		File product = runner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", ".jar");
		assertEquals(FileUtils.readFileToString(product, Charset.forName("UTF-8")), FileUtils.readFileToString(new File(env.getArtifactory1Folder(),
				Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID,
				untillArtifactId, "123.4", ".jar")), Charset.forName("UTF-8")));
		File ublComponent = new File(runner.getRepository(), Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID, ublArtifactId,
				"22.2", ".war"));
		assertTrue(ublComponent.exists());
		assertEquals(FileUtils.readFileToString(ublComponent, Charset.forName("UTF-8")), TEST_UBL_22_2_CONTENT);
		File transitiveDep = new File(runner.getRepository(), Utils.coordsToRelativeFilePath(TEST_AXIS_GROUP_ID,
				axisJaxrpcArtifact, "1.4", ".jar"));
		assertTrue(transitiveDep.exists());
		assertEquals(FileUtils.readFileToString(transitiveDep, Charset.forName("UTF-8")), TEST_DEP_CONTENT);
	}

	@Test
	public void testUnzip() throws Exception {
		IInstaller unziper = new UnzipArtifact(new File(TEST_ARTIFACTORY_DIR), pathToUntill);
		unziper.install();
		File metainf = new File(TEST_ARTIFACTORY_DIR, "META-INF");
		Manifest mf = new Manifest();
		assertTrue(metainf.exists());
		mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "ProductStructureDataLoader");
		Manifest unzipMf;
		try(FileInputStream fis = new FileInputStream(new File(metainf, "MANIFEST.MF"))) {
			unzipMf = new Manifest(fis);
		}
		assertEquals(mf.getMainAttributes(), unzipMf.getMainAttributes());
	}
}

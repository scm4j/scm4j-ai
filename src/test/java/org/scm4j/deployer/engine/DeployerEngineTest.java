package org.scm4j.deployer.engine;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IDeploymentContext;
import org.scm4j.deployer.engine.deployers.OkDeployer;
import org.scm4j.deployer.engine.exceptions.EProductListEntryNotFound;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.scm4j.deployer.api.DeploymentResult.ALREADY_INSTALLED;
import static org.scm4j.deployer.api.DeploymentResult.OK;

public class DeployerEngineTest {

	public static final String TEST_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test")
			.getPath();
	private static final String TEST_UBL_22_2_CONTENT = "ubl 22.2 artifact content";
	private static final String TEST_DEP_CONTENT = "dependency content";
	private static final String TEST_UNTILL_GROUP_ID = "eu.untill";
	private static final String TEST_JOOQ_GROUP_ID = "org.jooq";
	private static final String TEST_AXIS_GROUP_ID = "org.apache.axis";
	private static final String UNTILL_ARTIFACT_ID = "unTill";
	private static final String UNTILL_COORDS = TEST_UNTILL_GROUP_ID + ":" + UNTILL_ARTIFACT_ID + ":jar";
	private static final String RELATIVE_UNTILL_PATH = Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID,
			UNTILL_ARTIFACT_ID, "123.4", "jar", null);
	private static AITestEnvironment env = new AITestEnvironment();
	private static String ublArtifactId = "UBL";
	private static String axisJaxrpcArtifact = "axis-jaxrpc";

	public static String getTestDir() {
		return TEST_DIR;
	}

	@AfterClass
	public static void after() throws IOException {
		FileUtils.deleteDirectory(new File(TEST_DIR));
	}

	@BeforeClass
	public static void setUp() throws IOException {
		env.prepareEnvironment();
		ArtifactoryWriter aw = new ArtifactoryWriter(env.getArtifactory1Folder());
		aw.generateProductListArtifact();
		aw.installArtifact(TEST_UNTILL_GROUP_ID, UNTILL_ARTIFACT_ID, "124.5", "jar",
				"ProductStructureDataLoader", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UNTILL_GROUP_ID, "scm4j-deployer-installers", "0.1.0", "jar",
				"Executor", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UNTILL_GROUP_ID, "scm4j-deployer-api", "0.1.0", "jar",
				"Api", env.getArtifactory1Folder());
		aw.installArtifact(TEST_UNTILL_GROUP_ID, ublArtifactId, "22.2", "war",
				TEST_UBL_22_2_CONTENT, env.getArtifactory1Folder());
		aw.installArtifact(TEST_JOOQ_GROUP_ID, "jooq", "3.1.0", "jar",
				TEST_DEP_CONTENT, env.getArtifactory1Folder());
		aw = new ArtifactoryWriter(env.getArtifactory2Folder());
		aw.installArtifact(TEST_UNTILL_GROUP_ID, UNTILL_ARTIFACT_ID, "123.4", "jar",
				"ProductStructureDataLoader", env.getArtifactory1Folder());
		aw.installArtifact(TEST_AXIS_GROUP_ID, "axis", "1.4", "jar",
				TEST_DEP_CONTENT, env.getArtifactory2Folder());
		aw.installArtifact(TEST_AXIS_GROUP_ID, axisJaxrpcArtifact, "1.4", "jar",
				TEST_DEP_CONTENT, env.getArtifactory2Folder());
	}

	@After
	public void tearDown() throws IOException {
		FileUtils.deleteDirectory(new File(env.getEnvFolder().getPath()));
	}

	@Before
	public void before() throws IOException {
		env.createEnvironment();
	}

	@Test
	public void testGetVersions() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		de.listProducts();
		Set<String> versions = de.listProductVersions(UNTILL_ARTIFACT_ID).keySet();
		assertNotNull(versions);
		assertTrue(versions.containsAll(Arrays.asList(
				"123.4", "124.5")));
		assertTrue(versions.size() == 2);
	}

	@Test
	public void downloadUnknownProduct() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		try {
			de.download("xyz", "1234");
			fail();
		} catch (EProductNotFound e) {
		}
	}

	@Test
	public void listProductVersionsBeforeListProducts() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		try {
			de.listProductVersions(UNTILL_ARTIFACT_ID);
			fail();
		} catch (EProductListEntryNotFound e) {
		}
	}

	@Test
	public void noReposNoWork() {
		try {
			new DeployerEngine(null, env.getEnvFolder(), "random URL");
			fail();
		} catch (Exception e) {
		}
	}

	@Test
	public void testUnknownVersion() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		try {
			de.download(UNTILL_ARTIFACT_ID, "xyz");
			fail();
		} catch (EProductNotFound e) {
		}
	}

	@Test
	public void testUrls() {
		assertEquals(Utils.coordsToRelativeFilePath("", "guava", "20.0", "jar",
				null),
				new File("/guava/20.0/guava-20.0.jar").getPath());
		assertEquals(Utils.coordsToRelativeFilePath("com.google.guava", "guava", "20.0",
				"jar", null),
				new File("com/google/guava/guava/20.0/guava-20.0.jar").getPath());
	}

	@Test
	public void testLoadRepos() throws Exception {
		Downloader loader = new Downloader(null, env.getEnvFolder(), env.getArtifactory1Url());
		loader.getProductList().readFromProductList();
		List<ArtifactoryReader> repos = loader.getProductList().getRepos();
		assertNotNull(repos);
		repos.containsAll(Arrays.asList(
				StringUtils.appendIfMissing(env.getArtifactory1Url(), "/"),
				StringUtils.appendIfMissing(env.getArtifactory2Url(), "/")));
	}

	@Test
	public void testDownloadAndDeployProduct() throws Exception {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		de.download(UNTILL_ARTIFACT_ID, "123.4");
		String relativePath = Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID,
				UNTILL_ARTIFACT_ID, "123.4", "jar", null);
		File testFile = new File(de.getDownloader().getPortableRepository(), relativePath);
		File coordsToFileinArtifactory = new File(env.getArtifactory2Folder(), relativePath);
		assertTrue(FileUtils.contentEquals(testFile, coordsToFileinArtifactory));
		testFile = new File(de.getDownloader().getPortableRepository(), Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID, ublArtifactId,
				"22.2", "war", null));
		assertTrue(testFile.exists());
		assertEquals(FileUtils.readFileToString(testFile, Charset.forName("UTF-8")), TEST_UBL_22_2_CONTENT);
		testFile = new File(de.getDownloader().getPortableRepository(), Utils.coordsToRelativeFilePath(TEST_AXIS_GROUP_ID,
				axisJaxrpcArtifact, "1.4", "jar", null));
		assertFalse(testFile.exists());
	}

	@Test
	public void testDownloadAndDeployProductFromLocalHost() throws Exception {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		de.download(UNTILL_ARTIFACT_ID, "123.4");
		File product = new File(de.getDownloader().getPortableRepository(), RELATIVE_UNTILL_PATH);
		de = new DeployerEngine(null, env.getBaseTestFolder(),
				de.getDownloader().getWorkingRepository().toURI().toURL().toString());
		de.download(UNTILL_ARTIFACT_ID, "123.4");
		File product1 = new File(de.getDownloader().getPortableRepository(), RELATIVE_UNTILL_PATH);
		assertTrue(FileUtils.contentEquals(product, product1));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testDownloadAndRefreshProducts() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		assertEquals(de.listProducts(), Collections.singletonList(UNTILL_ARTIFACT_ID));
		//changing product list
		Map entry = new HashMap<>();
		Map<String, String> map = new HashMap<>();
		map.put("some", "stuff");
		entry.put(ProductList.PRODUCTS, map);
		entry.put(ProductList.REPOSITORIES, new ArrayList<>(Collections.singletonList("file://some repos")));
		Utils.writeYaml(entry, new File(de.getDownloader().getProductList().getLocalProductList().toString()));
		List<String> list = de.listProducts();
		assertEquals(list, Collections.singletonList("some"));
		//reload product list
		List<String> refreshProducts = de.refreshProducts();
		assertEquals(refreshProducts, Collections.singletonList(UNTILL_ARTIFACT_ID));
	}

	@Test
	public void testDownloadAndRefreshProductsVersions() throws Exception {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		de.listProducts();
		Map<String, Boolean> testMap = new LinkedHashMap<>();
		testMap.put("123.4", false);
		testMap.put("124.5", false);
		assertEquals(de.listProductVersions(UNTILL_ARTIFACT_ID), testMap);
		//changing product versions
		Map<String, Set<String>> entry = new HashMap<>();
		entry.put(UNTILL_ARTIFACT_ID, new HashSet<>(Collections.singletonList("777")));
		entry.put("haha", new HashSet<>(Collections.singletonList("1234")));
		try (FileWriter writer = new FileWriter(new File(de.getDownloader().getProductList().getVersionsYml().toString()))) {
			DumperOptions options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			Yaml yaml = new Yaml(options);
			String yamlOtput = yaml.dump(entry);
			writer.write(yamlOtput);
		}
		testMap.clear();
		testMap.put("777", false);
		assertEquals(de.listProductVersions(UNTILL_ARTIFACT_ID), testMap);
		//reload version of specific product
		testMap.clear();
		testMap.put("123.4", false);
		testMap.put("124.5", false);
		assertEquals(de.refreshProductVersions(UNTILL_ARTIFACT_ID), testMap);
		de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		de.listProducts();
		assertEquals(de.listProductVersions(UNTILL_ARTIFACT_ID), testMap);
		de.download(UNTILL_ARTIFACT_ID, "123.4");
		testMap.replace("123.4", false, true);
		assertEquals(de.listProductVersions(UNTILL_ARTIFACT_ID), testMap);
		FileUtils.forceDelete(de.getDownloader().getProductList().getVersionsYml());
		testMap.clear();
		File metadataFolder1 = new File(env.getArtifactory1Folder(), Utils.coordsToFolderStructure(TEST_UNTILL_GROUP_ID, UNTILL_ARTIFACT_ID));
		File metadataFolder2 = new File(env.getArtifactory2Folder(), Utils.coordsToFolderStructure(TEST_UNTILL_GROUP_ID, UNTILL_ARTIFACT_ID));
		FileUtils.moveFileToDirectory(new File(metadataFolder1, ArtifactoryReader.METADATA_FILE_NAME), env.getEnvFolder(), false);
		FileUtils.moveFileToDirectory(new File(metadataFolder2, ArtifactoryReader.METADATA_FILE_NAME),
				env.getArtifactory1Folder(), true);
		FileUtils.deleteDirectory(new File(env.getEnvFolder(), "repository"));
		de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		de.listProducts();
		assertEquals(de.listProductVersions(UNTILL_ARTIFACT_ID), testMap);
		try {
			de.refreshProductVersions(UNTILL_ARTIFACT_ID);
			fail();
		} catch (RuntimeException e) {
		}
		FileUtils.moveFileToDirectory(new File(env.getEnvFolder(), ArtifactoryReader.METADATA_FILE_NAME), metadataFolder1, false);
		FileUtils.moveFileToDirectory(new File(env.getArtifactory1Folder(), ArtifactoryReader.METADATA_FILE_NAME), metadataFolder2, false);
	}

	@Test
	public void testCollectDeploymentContext() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		de.download(UNTILL_ARTIFACT_ID, "123.4");
		IDeploymentContext ctx = de.getDownloader().getDepCtx().get("UBL");
		assertEquals(ctx.getMainArtifact(), "UBL");
		assertTrue(ctx.getArtifacts().containsKey("UBL"));
		assertFalse(ctx.getArtifacts().containsKey("axis"));
	}

	@Test
	public void testCopyElementsFromPortableToWorkingFolder() throws Exception {
		DeployerEngine de = new DeployerEngine(env.getEnvFolder(), env.getBaseTestFolder(), env.getArtifactory1Url());
		de.download(UNTILL_ARTIFACT_ID, "123.4");
		File untillFile = new File(de.getDownloader().getPortableRepository(), RELATIVE_UNTILL_PATH);
		de.download(UNTILL_ARTIFACT_ID, "123.4");
		File localUntillFile = new File(de.getDownloader().getWorkingRepository(), RELATIVE_UNTILL_PATH);
		FileUtils.contentEquals(untillFile, localUntillFile);
	}

	@Test
	public void testDeploy() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		DeploymentResult dr = de.deploy(UNTILL_ARTIFACT_ID, "124.5");
		String groupAndArtifactId = TEST_UNTILL_GROUP_ID + ":" + UNTILL_ARTIFACT_ID;
		assertEquals(dr.getProductCoords(), groupAndArtifactId);
		assertEquals(dr, OK);
		dr = de.deploy(UNTILL_ARTIFACT_ID, "124.5");
		assertEquals(dr, ALREADY_INSTALLED);
		Map<String, Boolean> deployedVersion = de.listDeployedProducts(UNTILL_ARTIFACT_ID);
		assertTrue(deployedVersion.get("124.5"));
	}

	@Test
	public void testUndeploy() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
		DeploymentResult dr = de.deploy(UNTILL_ARTIFACT_ID, null);
		assertEquals(dr, OK);
		dr = de.deploy(UNTILL_ARTIFACT_ID, "124.5");
		assertEquals(dr, OK);
		assertEquals(1, OkDeployer.getCount());
		dr = de.deploy(UNTILL_ARTIFACT_ID, null);
		assertEquals(dr, OK);
		assertEquals(0, OkDeployer.getCount());
	}

	@Test
	public void testProductListFails() {
		DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory2Url());
		try {
			de.listProducts();
			fail();
		} catch (Exception e) {
		}
		try {
			de.refreshProducts();
		} catch (Exception e) {
		}
	}

	@Test
	public void testLombok() {
		try {
			Utils.getExportedClassName(env.getEnvFolder());
			fail();
		} catch (Exception e) {
		}
	}

}
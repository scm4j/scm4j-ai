package org.scm4j.deployer.engine;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.*;
import org.mockito.Mockito;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.engine.exceptions.EArtifactNotFound;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;
import org.scm4j.deployer.installers.UnzipArtifact;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

public class DeployerEngineTest {

    private static final String TEST_UBL_22_2_CONTENT = "ubl 22.2 artifact content";
    private static final String TEST_DEP_CONTENT = "dependency content";
    private static final String TEST_UNTILL_GROUP_ID = "eu.untill";
    private static final String TEST_JOOQ_GROUP_ID = "org.jooq";
    private static final String TEST_AXIS_GROUP_ID = "org.apache.axis";
    private static final String TEST_ARTIFACTORY_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test")
            .getPath();
    private static final String untillCoord = "eu.untill:unTILL:123.4@jar";

    private static AITestEnvironment env = new AITestEnvironment();

    private static String ublArtifactId = "UBL";
    private static String untillArtifactId = "unTILL";
    private static String jooqArtifact = "jooq";
    private static String axisArtifact = "axis";
    private static String axisJaxrpcArtifact = "axis-jaxrpc";
    private static File pathToUntill;
    private static String installersArtifactId = "installers";

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(new File(env.getEnvFolder().getPath()));
    }

    @AfterClass
    public static void after() throws IOException {
        FileUtils.deleteDirectory(new File(TEST_ARTIFACTORY_DIR));
    }

    @Before
    public void before() throws IOException {
        env.createEnvironment();
    }

    @BeforeClass
    public static void setUp() throws IOException {
        env.prepareEnvironment();
        ArtifactoryWriter aw = new ArtifactoryWriter(env.getArtifactory1Folder());
        aw.generateProductListArtifact();
        aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar",
                "ProductStructureDataLoader", env.getArtifactory1Folder());
        aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "124.5", "jar",
                "ProductStructureDataLoader", env.getArtifactory1Folder());
        aw.installArtifact(TEST_UNTILL_GROUP_ID, installersArtifactId, "1.1.0", "jar",
                "ExeRunner", env.getArtifactory1Folder());
        aw.installArtifact(TEST_UNTILL_GROUP_ID, ublArtifactId, "22.2", "war",
                TEST_UBL_22_2_CONTENT, env.getArtifactory1Folder());
        aw.installArtifact(TEST_JOOQ_GROUP_ID, jooqArtifact, "3.1.0", "jar",
                TEST_DEP_CONTENT, env.getArtifactory1Folder());
        aw = new ArtifactoryWriter(env.getArtifactory2Folder());
        aw.installArtifact(TEST_AXIS_GROUP_ID, axisArtifact, "1.4", "jar",
                TEST_DEP_CONTENT, env.getArtifactory2Folder());
        aw.installArtifact(TEST_AXIS_GROUP_ID, axisJaxrpcArtifact, "1.4", "jar",
                TEST_DEP_CONTENT, env.getArtifactory2Folder());
        pathToUntill = new File(env.getArtifactory1Folder(), Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID,
                untillArtifactId, "123.4", ".jar"));
    }

    @Test
    public void testGetProducts() throws IOException {
        DeployerRunner runner = new DeployerRunner(env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        Set<String> products = runner.getProductList().getProducts();
        assertNotNull(products);
        assertTrue(products.containsAll(Arrays.asList(
                "eu.untill:unTILL")));
        assertTrue(products.size() == 1);
    }

    @Test
    public void testGetVersions() throws Exception {
        DeployerRunner runner = new DeployerRunner(env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        List<String> versions = runner.getProductList().getProductVersions(TEST_UNTILL_GROUP_ID, untillArtifactId);
        assertNotNull(versions);
        assertTrue(versions.containsAll(Arrays.asList(
                "123.4", "124.5")));
        assertTrue(versions.size() == 2);
    }

    @Test
    public void testNoReposNoWork() throws FileNotFoundException {
        try {
            new DeployerRunner(env.getEnvFolder(), "random URL");
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void testUnknownArtifact() throws Exception {
        DeployerRunner runner = new DeployerRunner(env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        try {
            runner.getProductList().getProductVersions("eu.untill", "unknown artifact");
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
        DeployerRunner runner = new DeployerRunner(env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        try {
            runner.get(TEST_UNTILL_GROUP_ID, ublArtifactId, "unknown version", ".jar");
            fail();
        } catch (EArtifactNotFound e) {
        }
    }

    @Test
    public void testUrls() throws Exception {
        assertEquals(Utils.coordsToRelativeFilePath("", "guava", "20.0", "jar"),
                new File("/guava/20.0/guava-20.0.jar").getPath());
        assertEquals(Utils.coordsToRelativeFilePath("com.google.guava", "guava", "20.0", "jar"),
                new File("com/google/guava/guava/20.0/guava-20.0.jar").getPath());

        ArtifactoryReader repo = new ArtifactoryReader(env.getArtifactory1Url(), null, null);
        URL url = new URL(env.getArtifactory1Folder().toURI().toURL(), "com/google/guava/guava/20.0/guava-20.0.jar");
        URL expectedURL = repo.getProductUrl("com.google.guava", "guava", "20.0", "jar");
        assertEquals(expectedURL, url);
    }

    @Test
    public void testLoadRepos() {
        DeployerRunner runner = new DeployerRunner(env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        List<ArtifactoryReader> repos = runner.getProductList().getRepos();
        assertNotNull(repos);
        repos.containsAll(Arrays.asList(
                StringUtils.appendIfMissing(env.getArtifactory1Url(), "/"),
                StringUtils.appendIfMissing(env.getArtifactory2Url(), "/")));
    }

    @Test
    public void testDownloadAndDeployProduct() throws Exception {
        DeployerRunner mockedRunner = Mockito.spy(new DeployerRunner(env.getEnvFolder(), env.getArtifactory1Url()));
        mockedRunner.getProductList().readFromProductList();
        File testFile = mockedRunner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
        assertEquals(FileUtils.readFileToString(testFile, Charset.forName("UTF-8")), FileUtils.readFileToString(new File(env.getArtifactory1Folder(),
                Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID,
                        untillArtifactId, "123.4", "jar")), Charset.forName("UTF-8")));
        testFile = new File(mockedRunner.getRepository(), Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID, ublArtifactId,
                "22.2", ".war"));
        assertTrue(testFile.exists());
        assertEquals(FileUtils.readFileToString(testFile, Charset.forName("UTF-8")), TEST_UBL_22_2_CONTENT);
        testFile = new File(mockedRunner.getRepository(), Utils.coordsToRelativeFilePath(TEST_AXIS_GROUP_ID,
                axisJaxrpcArtifact, "1.4", "jar"));
        assertTrue(testFile.exists());
        assertEquals(FileUtils.readFileToString(testFile, Charset.forName("UTF-8")), TEST_DEP_CONTENT);

        //don't download second time
        testFile = mockedRunner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
        assertTrue(testFile.exists());
    }

    @Test
    public void testDownloadAndDeployProductFromLocalHost() throws Exception {
        DeployerEngine engine = new DeployerEngine(env.getEnvFolder(), env.getArtifactory1Url());
        engine.listProducts();
        File product = engine.download(untillCoord);
        engine = new DeployerEngine(env.getBaseTestFolder(), engine.getRunner().getRepository().toURI().toURL().toString());
        engine.listProducts();
        File product1 = engine.download(untillCoord);
        assertEquals(FileUtils.readFileToString(product, Charset.forName("UTF-8")), FileUtils.readFileToString(product1, Charset.forName("UTF-8")));
    }

    @Test
    public void testUnzip() throws Exception {
        IComponentDeployer unziper = new UnzipArtifact(new File(TEST_ARTIFACTORY_DIR), pathToUntill);
        unziper.deploy();
        File metainf = new File(TEST_ARTIFACTORY_DIR, "META-INF");
        Manifest mf = new Manifest();
        assertTrue(metainf.exists());
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "ProductStructureDataLoader");
        Manifest unzipMf;
        try (FileInputStream fis = new FileInputStream(new File(metainf, "MANIFEST.MF"))) {
            unzipMf = new Manifest(fis);
        }
        assertEquals(mf.getMainAttributes(), unzipMf.getMainAttributes());
    }

    @Test
    public void testAppendRepos() throws Exception {
        DeployerRunner runner = new DeployerRunner(env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        List<ArtifactoryReader> remoteRepos = runner.getProductList().getRepos();
        assertTrue(remoteRepos.size() == 2);
        assertEquals(remoteRepos.get(0).toString(),env.getArtifactory1Url());
        assertEquals(remoteRepos.get(1).toString(), env.getArtifactory2Url());
        File product = runner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
        DeployerRunner runner1 = new DeployerRunner(env.getBaseTestFolder(), runner.getRepository().toURI().toURL().toString());
        runner1.getProductList().readFromProductList();
        List<ArtifactoryReader> localRepo = runner1.getProductList().getRepos();
        assertEquals(localRepo.get(2).toString(), runner.getRepository().toURI().toURL().toString());
    }

    @Test
    public void testDownloadAndRefreshProducts() throws Exception {
        DeployerEngine de = new DeployerEngine(env.getEnvFolder(), env.getArtifactory1Url());
        assertEquals(de.listProducts(), Arrays.asList("unTILL"));
        //changing product list
        Map<String, ArrayList<String>> entry = new HashMap<>();
        entry.put(ProductList.PRODUCTS, new ArrayList<>(Arrays.asList("some:stuff")));
        entry.put(ProductList.REPOSITORIES, new ArrayList<>(Arrays.asList("file://some repos")));
        try(FileWriter writer = new FileWriter(new File(de.getRunner().getProductList().getLocalProductList().toString()))) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            String yamlOtput = yaml.dump(entry);
            writer.write(yamlOtput);
        }
        assertEquals(de.listProducts(), Arrays.asList("stuff"));
        //reload product list
        assertEquals(de.refreshProducts(), Arrays.asList("unTILL"));
    }

    @Test
    public void testDownloadAndRefreshProductsVersions() throws Exception {
        DeployerEngine de = new DeployerEngine(env.getEnvFolder(), env.getArtifactory1Url());
        de.getRunner().getProductList().readFromProductList();
        Map<String, Boolean> testMap = new LinkedHashMap<>();
        testMap.put("123.4", false);
        testMap.put("124.5", false);
        assertEquals(de.listProductVersions(untillArtifactId), testMap);
        //changing product versions
        Map<String, ArrayList<String>> entry = new HashMap<>();
        entry.put(untillArtifactId, new ArrayList<>(Arrays.asList("777")));
        entry.put("haha", new ArrayList<>(Arrays.asList("1234")));
        try(FileWriter writer = new FileWriter(new File(de.getRunner().getProductList().getVersionsYml().toString()))) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            String yamlOtput = yaml.dump(entry);
            writer.write(yamlOtput);
        }
        testMap.clear();
        testMap.put("777", false);
        assertEquals(de.listProductVersions(untillArtifactId), testMap);
        //reload version of specific product
        testMap.clear();
        testMap.put("123.4", false);
        testMap.put("124.5", false);
        assertEquals(de.refreshProductVersions(untillArtifactId),testMap);
        de = new DeployerEngine(env.getEnvFolder(), env.getArtifactory1Url());
        de.getRunner().getProductList().readFromProductList();
        assertEquals(de.listProductVersions(untillArtifactId), testMap);
        de.download(untillCoord);
        testMap.replace("123.4", false, true);
        assertEquals(de.listProductVersions(untillArtifactId), testMap);
    }

}
package org.scm4j.deployer.engine;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.scm4j.deployer.api.DeploymentContext;
import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DeployerEngineTest {

    private static final String TEST_UBL_22_2_CONTENT = "ubl 22.2 artifact content";
    private static final String TEST_DEP_CONTENT = "dependency content";
    private static final String TEST_UNTILL_GROUP_ID = "eu.untill";
    private static final String TEST_JOOQ_GROUP_ID = "org.jooq";
    private static final String TEST_AXIS_GROUP_ID = "org.apache.axis";
    private static final String TEST_ARTIFACTORY_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test")
            .getPath();

    private static AITestEnvironment env = new AITestEnvironment();

    private static String ublArtifactId = "UBL";
    private static String untillArtifactId = "unTILL";
    private static String axisJaxrpcArtifact = "axis-jaxrpc";

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
        aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "124.5", "jar",
                "ProductStructureDataLoader", env.getArtifactory1Folder());
        aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "124.5", "jar",
                "ProductStructureDataLoader", env.getArtifactory1Folder());
        aw.installArtifact(TEST_UNTILL_GROUP_ID, "installers", "0.1.0", "jar",
                "ExeRunner", env.getArtifactory1Folder());
        aw.installArtifact(TEST_UNTILL_GROUP_ID, "scm4j-deployer-api", "0.1.0", "jar",
                "Api", env.getArtifactory1Folder());
        aw.installArtifact(TEST_UNTILL_GROUP_ID, ublArtifactId, "22.2", "war",
                TEST_UBL_22_2_CONTENT, env.getArtifactory1Folder());
        aw.installArtifact(TEST_JOOQ_GROUP_ID, "jooq", "3.1.0", "jar",
                TEST_DEP_CONTENT, env.getArtifactory1Folder());
        aw = new ArtifactoryWriter(env.getArtifactory2Folder());
        aw.installArtifact(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar",
                "ProductStructureDataLoader", env.getArtifactory1Folder());
        aw.installArtifact(TEST_AXIS_GROUP_ID, "axis", "1.4", "jar",
                TEST_DEP_CONTENT, env.getArtifactory2Folder());
        aw.installArtifact(TEST_AXIS_GROUP_ID, axisJaxrpcArtifact, "1.4", "jar",
                TEST_DEP_CONTENT, env.getArtifactory2Folder());
    }

    @Test
    public void testGetProducts() throws Exception {
        DeployerRunner runner = new DeployerRunner(null,env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        Set<String> products = runner.getProductList().getProducts();
        assertNotNull(products);
        assertTrue(products.containsAll(Collections.singletonList(
                "eu.untill:unTILL")));
        assertTrue(products.size() == 1);
    }

    @Test
    public void testGetVersions() throws Exception {
        DeployerRunner runner = new DeployerRunner(null,env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        Set<String> versions = runner.getProductList().getProductsVersions().get(untillArtifactId);
        assertNotNull(versions);
        assertTrue(versions.containsAll(Arrays.asList(
                "123.4", "124.5")));
        assertTrue(versions.size() == 2);
    }

    @Test
    public void testNoReposNoWork() throws FileNotFoundException {
        try {
            new DeployerRunner(null, env.getEnvFolder(), "random URL");
            fail();
        } catch (Exception e) {

        }
    }

    @Test
    public void testUnknownProduct() throws Exception {
        DeployerRunner runner = new DeployerRunner(null,env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        assertEquals(Collections.emptyList(),
                runner.getProductList().getProductListReader().getProductVersions("eu.untill", "unknown artifact"));

        try {
            runner.get("eu.untill", "unknown artifact", "version", "extension");
            fail();
        } catch (EProductNotFound e) {
        }
    }

    @Test
    public void testUnknownVersion() throws Exception {
        DeployerRunner runner = new DeployerRunner(null,env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        try {
            runner.get(TEST_UNTILL_GROUP_ID, ublArtifactId, "unknown version", ".jar");
            fail();
        } catch (EProductNotFound e) {
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
    public void testLoadRepos() throws Exception {
        DeployerRunner runner = new DeployerRunner(null,env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        Set<ArtifactoryReader> repos = runner.getProductList().getRepos();
        assertNotNull(repos);
        repos.containsAll(Arrays.asList(
                StringUtils.appendIfMissing(env.getArtifactory1Url(), "/"),
                StringUtils.appendIfMissing(env.getArtifactory2Url(), "/")));
    }

    @Test
    public void testDownloadAndDeployProduct() throws Exception {
        DeployerRunner runner = new DeployerRunner(null,env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        File testFile = runner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
        assertTrue(FileUtils.contentEquals(testFile,new File(env.getArtifactory2Folder(),
                Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID,
                        untillArtifactId, "123.4", "jar"))));
        testFile = new File(runner.getRepository(), Utils.coordsToRelativeFilePath(TEST_UNTILL_GROUP_ID, ublArtifactId,
                "22.2", ".war"));
        assertTrue(testFile.exists());
        assertEquals(FileUtils.readFileToString(testFile, Charset.forName("UTF-8")), TEST_UBL_22_2_CONTENT);
        testFile = new File(runner.getRepository(), Utils.coordsToRelativeFilePath(TEST_AXIS_GROUP_ID,
                axisJaxrpcArtifact, "1.4", "jar"));
        assertTrue(testFile.exists());
        assertEquals(FileUtils.readFileToString(testFile, Charset.forName("UTF-8")), TEST_DEP_CONTENT);
    }

    @Test
    public void testDownloadAndDeployProductFromLocalHost() throws Exception {
        DeployerEngine engine = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
        engine.listProducts();
        File product = engine.download(untillArtifactId, "123.4");
        engine = new DeployerEngine(null, env.getBaseTestFolder(), engine.getRunner().getRepository().toURI().toURL().toString());
        engine.listProducts();
        File product1 = engine.download(untillArtifactId, "123.4");
        assertTrue(FileUtils.contentEquals(product, product1));
    }

    @Test
    public void testAppendRepos() throws Exception {
        DeployerRunner runner = new DeployerRunner(null,env.getEnvFolder(), env.getArtifactory1Url());
        runner.getProductList().readFromProductList();
        Set<ArtifactoryReader> remoteRepos = runner.getProductList().getRepos();
        assertTrue(remoteRepos.size() == 2);
        Set<String> reposNames = remoteRepos.stream().map(ArtifactoryReader::toString).collect(Collectors.toSet());
        assertTrue(reposNames.containsAll(Arrays.asList(env.getArtifactory1Url()
                ,env.getArtifactory2Url())));
        runner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
        DeployerRunner runner1 = new DeployerRunner(null,env.getBaseTestFolder(), runner.getRepository().toURI().toURL().toString());
        runner1.getProductList().readFromProductList();
        remoteRepos = runner1.getProductList().getRepos();
        reposNames = remoteRepos.stream().map(ArtifactoryReader::toString).collect(Collectors.toSet());
        assertTrue(reposNames.contains(runner.getRepository().toURI().toURL().toString()));
    }

    @Test
    public void testDownloadAndRefreshProducts() throws Exception {
        DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
        assertEquals(de.listProducts(), Collections.singletonList("unTILL"));
        //changing product list
        Map<String, Set<String>> entry = new HashMap<>();
        entry.put(ProductList.PRODUCTS, new HashSet<>(Collections.singletonList("some:stuff")));
        entry.put(ProductList.REPOSITORIES, new HashSet<>(Collections.singletonList("file://some repos")));
        try(FileWriter writer = new FileWriter(new File(de.getRunner().getProductList().getLocalProductList().toString()))) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            String yamlOtput = yaml.dump(entry);
            writer.write(yamlOtput);
        }
        assertEquals(de.listProducts(), Collections.singletonList("stuff"));
        //reload product list
        assertEquals(de.refreshProducts(), Collections.singletonList("unTILL"));
    }

    @Test
    public void testDownloadAndRefreshProductsVersions() throws Exception {
        DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
        de.listProducts();
        Map<String, Boolean> testMap = new LinkedHashMap<>();
        testMap.put("123.4", false);
        testMap.put("124.5", false);
        assertEquals(de.listProductVersions(untillArtifactId), testMap);
        //changing product versions
        Map<String, Set<String>> entry = new HashMap<>();
        entry.put(untillArtifactId, new HashSet<>(Collections.singletonList("777")));
        entry.put("haha", new HashSet<>(Collections.singletonList("1234")));
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
        de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
        de.getRunner().getProductList().readFromProductList();
        assertEquals(de.listProductVersions(untillArtifactId), testMap);
        de.download(untillArtifactId, "123.4");
        testMap.replace("123.4", false, true);
        assertEquals(de.listProductVersions(untillArtifactId), testMap);
        FileUtils.forceDelete(de.getRunner().getProductList().getVersionsYml());
        testMap.clear();
        File metadataFolder1 = new File(env.getArtifactory1Folder(),Utils.coordsToFolderStructure(TEST_UNTILL_GROUP_ID, untillArtifactId));
        File metadataFolder2 = new File(env.getArtifactory2Folder(),Utils.coordsToFolderStructure(TEST_UNTILL_GROUP_ID, untillArtifactId));
        FileUtils.moveFileToDirectory(new File(metadataFolder1, ArtifactoryReader.METADATA_FILE_NAME), env.getEnvFolder(),false);
        FileUtils.moveFileToDirectory(new File(metadataFolder2, ArtifactoryReader.METADATA_FILE_NAME),
                env.getArtifactory1Folder(),true);
        FileUtils.deleteDirectory(new File(env.getEnvFolder(), "repository"));
        de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
        de.listProducts();
        assertEquals(de.listProductVersions(untillArtifactId), testMap);
        try {
            de.refreshProductVersions(untillArtifactId);
            fail();
        } catch (RuntimeException e) {}
        FileUtils.moveFileToDirectory(new File(env.getEnvFolder(),ArtifactoryReader.METADATA_FILE_NAME), metadataFolder1,false);
        FileUtils.moveFileToDirectory(new File(env.getArtifactory1Folder(), ArtifactoryReader.METADATA_FILE_NAME), metadataFolder2, false);
    }

    @Test
    public void testCollectDeploymentContext() throws Exception {
        DeployerEngine de = new DeployerEngine(null, env.getEnvFolder(), env.getArtifactory1Url());
        de.listProducts();
        de.download(untillArtifactId, "123.4");
        DeploymentContext ctx = de.getRunner().getDepCtx().get("UBL");
        assertEquals(ctx.getMainArtifact(), "UBL");
        assertTrue(ctx.getArtifacts().containsKey("UBL"));
        assertTrue(ctx.getArtifacts().containsKey("axis"));
        assertEquals(ctx.getDeploymentURL(), new URL("file://localhost/C:/tools/UBL"));
        assertNull(ctx.getParams());
    }

}

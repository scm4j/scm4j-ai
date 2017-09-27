package org.scm4j.deployer.engine;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.mockito.Mockito;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.engine.exceptions.EArtifactNotFound;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;
import org.scm4j.deployer.installers.UnzipArtifact;

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

import static org.junit.Assert.*;

public class AIRunnerTest {

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
        List<String> versions = runner.getProductList().getProductVersions(TEST_UNTILL_GROUP_ID, untillArtifactId);
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
        AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
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
        AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
        List<ArtifactoryReader> repos = runner.getProductList().getRepos();
        assertNotNull(repos);
        repos.containsAll(Arrays.asList(
                StringUtils.appendIfMissing(env.getArtifactory1Url(), "/"),
                StringUtils.appendIfMissing(env.getArtifactory2Url(), "/")));
    }

    @Test
    public void testDownloadAndDeployProduct() throws Exception {
        AIRunner mockedRunner = Mockito.spy(new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null));
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
        Mockito.verify(mockedRunner, Mockito.times(1))
                .download(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
        Mockito.verify(mockedRunner, Mockito.times(2))
                .queryLocal(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
    }

    @Test
    public void testDownloadAndDeployProductFromLocalHost() throws Exception {
        AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
        File product = runner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
        runner = new AIRunner(env.getBaseTestFolder(), runner.getRepository().toURI().toURL().toString(), null, null);
        File product1 = runner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
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
    public void testChangeReposOnLocalRepo() throws Exception {
        AIRunner runner = new AIRunner(env.getEnvFolder(), env.getArtifactory1Url(), null, null);
        List<ArtifactoryReader> remoteRepos = runner.getProductList().getRepos();
        assertTrue(remoteRepos.size() == 2);
        assertEquals(remoteRepos.get(0).toString(),env.getArtifactory1Url());
        assertEquals(remoteRepos.get(1).toString(), env.getArtifactory2Url());
        File product = runner.get(TEST_UNTILL_GROUP_ID, untillArtifactId, "123.4", "jar");
        AIRunner runner1 = new AIRunner(env.getBaseTestFolder(), runner.getRepository().toURI().toURL().toString(), null, null);
        List<ArtifactoryReader> localRepo = runner1.getProductList().getRepos();
        assertTrue(localRepo.size() == 3);
        assertEquals(localRepo.get(2).toString(), runner.getRepository().toURI().toURL().toString());
    }

    @Test
    public void testAvailableProducts() {
        DeployerEngine de = new DeployerEngine(env.getEnvFolder(), env.getArtifactory1Url());
        assertEquals(de.listAvailableProducts(), Arrays.asList("unTILL"));
    }

    @Test
    public void testAvailableVersions() {
        DeployerEngine de = new DeployerEngine(env.getEnvFolder(), env.getArtifactory1Url());
        assertEquals(de.listAvailableProductVersions("unTILL"), Arrays.asList("123.4", "124.5"));
    }

    @Test
    public void testMarkAfterDownload() {
        DeployerEngine de = new DeployerEngine(env.getEnvFolder(), env.getArtifactory1Url());
        de.deploy(TEST_UNTILL_GROUP_ID + ":" + untillArtifactId + ":123.4@jar");
        assertTrue(de.getRunner().getProductList().getProductListEntry().get(ProductList.DOWNLOADED_PRODUCTS)
                .contains(untillArtifactId + "-123.4"));
        de.deploy(TEST_UNTILL_GROUP_ID + ":" + untillArtifactId + ":124.5@jar");
        assertTrue(de.getRunner().getProductList().getProductListEntry().get(ProductList.DOWNLOADED_PRODUCTS)
                .contains(untillArtifactId + "-123.4"));
        assertTrue(de.getRunner().getProductList().getProductListEntry().get(ProductList.DOWNLOADED_PRODUCTS)
                .contains(untillArtifactId + "-124.5"));
        assertEquals(de.listDownloadedProducts(), Arrays.asList("unTILL-123.4", "unTILL-124.5"));
    }

}

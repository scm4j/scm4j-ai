package org.scm4j.deployer.engine;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.Deployer.Command;
import org.scm4j.deployer.engine.deployers.FailedDeployer;
import org.scm4j.deployer.engine.deployers.OkDeployer;
import org.scm4j.deployer.engine.deployers.RebootDeployer;
import org.scm4j.deployer.engine.exceptions.EIncompatibleApiVersion;
import org.scm4j.deployer.engine.products.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.scm4j.deployer.api.DeploymentResult.*;
import static org.scm4j.deployer.engine.Deployer.Command.DEPLOY;
import static org.scm4j.deployer.engine.Deployer.Command.UNDEPLOY;

public class DeployerTest {

    @AfterClass
    public static void afterClass() throws Exception {
        FileUtils.forceDelete(new File(DeployerEngineTest.getTestDir()));
    }

    @BeforeClass
    public static void beforeClass() {
        File testDir = new File(DeployerEngineTest.getTestDir());
        testDir.mkdirs();
    }

    @Test
    public void testCompareProductStructures() {
        Downloader downloader = mock(Downloader.class);
        Deployer dep = new Deployer(new File("C:/"), downloader);
        assertEquals(new File("C:/"), dep.getWorkingFolder());
        IProductStructure okStructure = new OkProduct().getProductStructure();
        IProductStructure failStructure = new FailProduct().getProductStructure();
        IProductStructure rebootStructure = new RebootProduct().getProductStructure();
        Map<Command, List<IComponent>> components = dep.compareProductStructures(okStructure, failStructure);
        List<IComponent> list = new ArrayList<>();
        list.add(failStructure.getComponents().get(1));
        assertEquals(Collections.emptyList(), components.get(DEPLOY));
        assertEquals(list, components.get(UNDEPLOY));
        failStructure = new FailProduct().getProductStructure();
        components = dep.compareProductStructures(failStructure, rebootStructure);
        assertEquals(Collections.emptyList(), components.get(DEPLOY));
        assertEquals(Collections.emptyList(), components.get(UNDEPLOY));
        failStructure = new FailProduct().getProductStructure();
        okStructure = new OkProduct().getProductStructure();
        components = dep.compareProductStructures(failStructure, okStructure);
        assertEquals(Collections.emptyList(), components.get(UNDEPLOY));
        assertEquals(list, components.get(DEPLOY));
    }

    @Test
    public void testDeploy() {
        IDownloader downloader = mockDeploymentContext();
        Deployer dep = new Deployer(new File("C:/"), downloader);
        IProduct okProduct = new OkProduct();
        DeployedProduct prod = createDeployedProduct();
        IProduct failProduct = new FailProduct();
        IProduct rebootProduct = new RebootProduct();
        DeploymentResult dr = dep.compareAndDeployProducts(okProduct, null, "ok", "1.0");
        assertEquals("file://C:/unTill", dep.getDeploymentPath());
        assertEquals(OK, dr);
        dr = dep.compareAndDeployProducts(new EmptyProduct(), prod, "ok", "1.0");
        assertEquals(OK, dr);
        dr = dep.compareAndDeployProducts(failProduct, prod, "ok", "1.0");
        assertEquals(FAILED, dr);
        assertEquals(1, FailedDeployer.getCount());
        assertEquals(0, OkDeployer.getCount());
        dr = dep.compareAndDeployProducts(rebootProduct, prod, "ok", "1.0");
        assertEquals(NEED_REBOOT, dr);
        assertEquals(1, RebootDeployer.getCount());
        assertEquals(0, OkDeployer.getCount());
    }

    private IDownloader mockDeploymentContext() {
        OkDeployer.zeroCount();
        IDownloader downloader = mock(IDownloader.class);
        String ubl = "UBL";
        String axis = "axis";
        String jooq = "jooq";
        when(downloader.getContextByArtifactId(ubl)).thenReturn(new DeploymentContext(ubl));
        when(downloader.getContextByArtifactId(axis)).thenReturn(new DeploymentContext(axis));
        when(downloader.getContextByArtifactId(jooq)).thenReturn(new DeploymentContext(jooq));
        return downloader;
    }

    private static DeployedProduct createDeployedProduct() {
        DeployedProduct prod = new DeployedProduct();
        prod.setDeploymentPath("C:/");
        prod.setProductVersion("1.0");
        prod.setProductStructure(new OkProduct().getProductStructure());
        return prod;
    }

    @Test
    public void testDeployDependent() throws Exception {
        IDownloader downloader = mockDeploymentContext();
        when(downloader.getProductFile(anyString())).thenReturn(new File("C:/"));
        when(downloader.getProduct()).thenReturn(new OkProduct());
        DeployedProduct prod = createDeployedProduct();
        DependentProduct depProd = new DependentProduct();
        Deployer dep = new Deployer(new File(DeployerEngineTest.getTestDir()), downloader);
        DeploymentResult res = dep.compareAndDeployProducts(depProd, null, "ok", "1.0");
        assertEquals(OK, res);
        assertEquals(6, OkDeployer.getCount());
        res = dep.compareAndDeployProducts(new EmptyProduct(), prod, "ok", "1.0");
        assertEquals(OK, res);
        assertEquals(3, OkDeployer.getCount());
    }

    @Test
    public void testLegacyProduct() throws Exception {
        IDownloader downloader = mockDeploymentContext();
        when(downloader.getProductFile(anyString())).thenReturn(new File("C:/"));
        when(downloader.getProduct()).thenReturn(new LegacyProduct());
        Deployer dep = new Deployer(new File(DeployerEngineTest.getTestDir()), downloader);
        DeploymentResult dr = dep.deploy(new DefaultArtifact("eu.untill:unTill:jar:1.0"));
        assertEquals(ALREADY_INSTALLED, dr);
        afterClass();
        beforeClass();
        dr = dep.deploy(new DefaultArtifact("eu.untill:unTill:jar:123.4"));
        assertEquals(OK, dr);
        afterClass();
        beforeClass();
        dr = dep.deploy(new DefaultArtifact("eu.untill:unTill:jar:0.4"));
        assertEquals(NEWER_VERSION_EXISTS, dr);
    }

    @Test
    public void testProductDescription() throws Exception {
        ProductDescription pd = new ProductDescription();
        pd.setProductVersion("1.0");
        pd.setDeploymentPath("C:/");
        pd.setDeploymentTime(System.currentTimeMillis());
        Thread.sleep(1);
        ProductDescription pd1 = new ProductDescription();
        pd1.setProductVersion("1.0");
        pd1.setDeploymentPath("C:/");
        pd1.setDeploymentTime(System.currentTimeMillis());
        assertTrue(pd.equals(pd1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIncompatibleApi() throws Exception {
        IDownloader downloader = mock(IDownloader.class);
        when(downloader.getProductFile(anyString())).thenThrow(EIncompatibleApiVersion.class);
        Deployer dep = new Deployer(new File("C:/"), downloader);
        try {
            dep.deploy(new DefaultArtifact("x:y:z:1.0"));
            fail();
        } catch (EIncompatibleApiVersion e) {
        }
    }
}

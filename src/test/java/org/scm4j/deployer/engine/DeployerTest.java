package org.scm4j.deployer.engine;

import org.junit.Test;
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.Deployer.Command;
import org.scm4j.deployer.engine.deployers.FailedDeployer;
import org.scm4j.deployer.engine.deployers.OkDeployer;
import org.scm4j.deployer.engine.deployers.RebootDeployer;
import org.scm4j.deployer.engine.products.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.scm4j.deployer.api.DeploymentResult.*;
import static org.scm4j.deployer.engine.Deployer.Command.DEPLOY;
import static org.scm4j.deployer.engine.Deployer.Command.UNDEPLOY;

public class DeployerTest {

    @Test
    public void testCompareProductStructures() {
        Downloader downloader = mock(Downloader.class);
        Deployer dep = new Deployer(null, new File("C:/"), downloader);
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
    public void testDeploy() throws Exception {
        OkDeployer.zeroCount();
        IDownloader downloader = mock(IDownloader.class);
        String ubl = "UBL";
        String axis = "axis";
        String jooq = "jooq";
        when(downloader.getContextByArtifactId(ubl)).thenReturn(new DeploymentContext(ubl));
        when(downloader.getContextByArtifactId(axis)).thenReturn(new DeploymentContext(axis));
        when(downloader.getContextByArtifactId(jooq)).thenReturn(new DeploymentContext(jooq));
        Deployer dep = new Deployer(null, new File("C:/"), downloader);
        IProduct okProduct = new OkProduct();
        DeployedProduct prod = new DeployedProduct();
        prod.setDeploymentPath("C:/");
        prod.setProductVersion("1.0");
        prod.setProductStructure(okProduct.getProductStructure());
        IProduct failProduct = new FailProduct();
        IProduct rebootProduct = new RebootProduct();
        DeploymentResult dr = dep.deploy(okProduct, null, "ok", "1.0");
        assertEquals(OK, dr);
        dr = dep.deploy(new EmptyProduct(), prod, "ok", "1.0");
        assertEquals(OK, dr);
        dr = dep.deploy(failProduct, prod, "ok", "1.0");
        assertEquals(FAILED, dr);
        assertEquals(1, FailedDeployer.getCount());
        assertEquals(0, OkDeployer.getCount());
        dr = dep.deploy(rebootProduct, prod, "ok", "1.0");
        assertEquals(NEED_REBOOT, dr);
        assertEquals(1, RebootDeployer.getCount());
        assertEquals(0, OkDeployer.getCount());
    }
}

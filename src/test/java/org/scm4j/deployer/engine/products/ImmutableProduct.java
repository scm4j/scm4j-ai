package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.IImmutable;
import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.engine.DeployerEngineTest;
import org.scm4j.deployer.engine.deployers.OkDeployer;

public class ImmutableProduct implements IProduct, IImmutable {

    public IProductStructure getProductStructure() {
        return ProductStructure.create(DeployerEngineTest.TEST_DIR + "/latest-test")
                .addComponent("eu.untill:UBL:war:22.2")
                .addComponentDeployer(new OkDeployer())
                .parent()
                .addComponent("org.apache.axis:axis:1.4")
                .addComponentDeployer(new OkDeployer())
                .addComponentDeployer(new OkDeployer())
                .parent();
    }
}

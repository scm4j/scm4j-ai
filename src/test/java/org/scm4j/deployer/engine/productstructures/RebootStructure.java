package org.scm4j.deployer.engine.productstructures;

import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.engine.deployers.OkDeployer;
import org.scm4j.deployer.engine.deployers.RebootDeployer;

public class RebootStructure implements IProduct {

    public IProductStructure getProductStructure() {
        return ProductStructure.create("file://C:/unTill")
                .addComponent("eu.untill:UBL:war:22.2")
                .addComponentDeployer(new OkDeployer())
                .parent()
                .addComponent("org.jooq:jooq:3.1.0")
                .addComponentDeployer(new RebootDeployer())
                .parent()
                .addComponent("org.apache.axis:axis:1.4")
                .addComponentDeployer(new OkDeployer())
                .addComponentDeployer(new OkDeployer())
                .parent();
    }
}

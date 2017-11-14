package org.scm4j.deployer.engine;

import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.engine.deployers.OkDeployer;

public class TestProductStructure {

    public static IProductStructure getProductStructure() {
        return ProductStructure.create("file://C:/unTill")
                .addComponent("eu.untill:UBL:war:22.2")
                .addAction(OkDeployer.class)
                .addParam("deploy", "")
                .parent()
                .parent()
                .addComponent("org.jooq:jooq:3.1.0")
                .addAction(OkDeployer.class)
                .addParam("deploy", "")
                .parent()
                .parent()
                .addComponent("org.apache.axis:axis:1.4")
                .addAction(OkDeployer.class)
                .addParam("deploy", "")
                .parent()
                .addAction(OkDeployer.class)
                .addParam("deploy", "")
                .parent()
                .parent();
    }
}

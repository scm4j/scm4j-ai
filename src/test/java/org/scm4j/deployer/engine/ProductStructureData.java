package org.scm4j.deployer.engine;

import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.installers.Executor;

public class ProductStructureData {

    public static IProductStructure getProductStructure() {
        return ProductStructure.create("file://C:/unTill")
                .addComponent("eu.untill:UBL:war:22.2")
                .addAction(Executor.class)
                .addParam("deploy", "")
                .parent()
                .parent();
    }
}

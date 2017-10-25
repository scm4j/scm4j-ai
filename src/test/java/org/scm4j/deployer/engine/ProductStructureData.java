package org.scm4j.deployer.engine;

import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;

public class ProductStructureData {

    public static IProductStructure getProductStructure() {
        return ProductStructure.create()
                .addComponent("eu.untill:UBL:war:22.2")
                .addAction("Executor")
                .addParam("1", "2")
                .parent()
                .parent();
    }
}

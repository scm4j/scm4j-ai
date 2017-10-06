package org.scm4j.deployer.engine;

import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.installers.Executor;

public class ProductStructureData {

    private static ProductStructure productStructure;

    private static void enterProduct() {
        productStructure = ProductStructure.create()
                .addComponent("eu.untill:UBL:war:22.2")
                .addAction(Executor.class)
                .addParam("1", "2")
                .parent()
                .parent();
    }

    public static IProductStructure getProductStructure() {
        enterProduct();
        return productStructure;
    }
}

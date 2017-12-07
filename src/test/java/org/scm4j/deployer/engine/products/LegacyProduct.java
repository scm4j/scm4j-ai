package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.deployers.OkDeployer;

public class LegacyProduct implements IProduct, ILegacyProduct {

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IDeployedProduct> T queryLegacyDeployedProduct() {
        DeployedProduct prod = new DeployedProduct();
        prod.setProductVersion("1.0");
        prod.setDeploymentPath("C:/");
        prod.setProductStructure(ProductStructure.create("")
                .addComponent("x:legacyComponent:1.0")
                .addComponentDeployer(new OkDeployer())
                .parent());
        return (T) prod;
    }

    @Override
    public IProductStructure getProductStructure() {
        return new OkProduct().getProductStructure();
    }
}

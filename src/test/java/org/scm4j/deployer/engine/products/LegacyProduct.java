package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.*;

public class LegacyProduct implements IProduct, ILegacyProduct {

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IDeployedProduct> T queryLegacyDeployedProduct() {
        DeployedProduct prod = new DeployedProduct();
        prod.setProductVersion("1.0");
        prod.setDeploymentPath("C:/");
        prod.setProductStructure(new FailProduct().getProductStructure());
        return (T) prod;
    }

    @Override
    public IProductStructure getProductStructure() {
        return new OkProduct().getProductStructure();
    }
}

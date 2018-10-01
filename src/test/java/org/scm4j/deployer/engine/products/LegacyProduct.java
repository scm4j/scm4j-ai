package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.DeployedProduct;
import org.scm4j.deployer.api.IDeployedProduct;
import org.scm4j.deployer.api.ILegacyProduct;
import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.engine.DeployerEngineTest;
import org.scm4j.deployer.engine.deployers.OkDeployer;

public class LegacyProduct implements IProduct, ILegacyProduct {

	public static final String LEGACY_VERSION = "1.0";

	@Override
	@SuppressWarnings("unchecked")
	public <T extends IDeployedProduct> T queryLegacyDeployedProduct() {
		DeployedProduct prod = new DeployedProduct();
		prod.setProductVersion(LEGACY_VERSION);
		prod.setDeploymentPath(DeployerEngineTest.TEST_DIR);
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

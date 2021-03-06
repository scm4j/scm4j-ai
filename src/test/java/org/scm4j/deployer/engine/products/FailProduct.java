package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.engine.DeployerEngineTest;
import org.scm4j.deployer.engine.deployers.FailedDeployer;
import org.scm4j.deployer.engine.deployers.OkDeployer;

public class FailProduct implements IProduct {

	public IProductStructure getProductStructure() {
		return ProductStructure.create(DeployerEngineTest.TEST_DIR)
				.addComponent("eu.untill:UBL:war:22.2")
				.addComponentDeployer(new OkDeployer())
				.parent()
				.addComponent("org.jooq:jooq:3.1.0")
				.addComponentDeployer(new OkDeployer())
				.addComponentDeployer(new FailedDeployer())
				.parent()
				.addComponent("org.apache.axis:axis:1.4")
				.addComponentDeployer(new OkDeployer())
				.parent();
	}
}

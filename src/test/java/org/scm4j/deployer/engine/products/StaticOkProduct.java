package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.engine.DeployerEngineTest;
import org.scm4j.deployer.engine.deployers.OkDeployer;

public class StaticOkProduct {
	public static IProductStructure getProductStructure() {
		return ProductStructure.create(DeployerEngineTest.TEST_DIR)
				.addComponent("eu.untill:UBL:war:22.2")
				.addComponentDeployer(new OkDeployer())
				.parent();
	}
}

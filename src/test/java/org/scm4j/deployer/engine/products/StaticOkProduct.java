package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;
import org.scm4j.deployer.engine.deployers.OkDeployer;

public class StaticOkProduct {
	public static IProductStructure getProductStructure() {
		return ProductStructure.create("file://C:/Program Files/unTill")
				.addComponent("eu.untill:UBL:war:22.2")
				.addComponentDeployer(new OkDeployer())
				.parent()
				.addComponent("org.jooq:jooq:3.1.0")
				.addComponentDeployer(new OkDeployer())
				.parent()
				.addComponent("org.apache.axis:axis:1.4")
				.addComponentDeployer(new OkDeployer())
				.addComponentDeployer(new OkDeployer())
				.parent();
	}
}

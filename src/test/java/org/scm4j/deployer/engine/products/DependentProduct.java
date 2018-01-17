package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.api.IProductStructure;

import java.util.Collections;
import java.util.List;

public class DependentProduct implements IProduct {

	@Override
	public IProductStructure getProductStructure() {
		return new OkProduct().getProductStructure();
	}

	@Override
	public List<String> getDependentProducts() {
		return Collections.singletonList("eu.untill:UBL:22.5");
	}
}

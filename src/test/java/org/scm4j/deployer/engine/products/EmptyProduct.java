package org.scm4j.deployer.engine.products;

import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;

public class EmptyProduct implements IProduct {

	@Override
	public IProductStructure getProductStructure() {
		return ProductStructure.createEmptyStructure();
	}
}

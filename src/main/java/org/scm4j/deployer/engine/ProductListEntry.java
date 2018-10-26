package org.scm4j.deployer.engine;

import lombok.Data;
import org.scm4j.deployer.api.ProductInfo;

import java.util.List;
import java.util.Map;

@Data
public class ProductListEntry {
	private final List<String> repositories;
	private final Map<String, ProductInfo> products;
}

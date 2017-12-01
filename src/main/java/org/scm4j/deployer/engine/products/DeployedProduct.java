package org.scm4j.deployer.engine.products;

import lombok.Data;
import org.scm4j.deployer.api.IDeployedProduct;
import org.scm4j.deployer.api.IProductStructure;

@Data
public class DeployedProduct implements IDeployedProduct {

    private String deploymentPath;
    private String productVersion;
    private IProductStructure productStructure;

}

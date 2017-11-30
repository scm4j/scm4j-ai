package org.scm4j.deployer.engine.products;

import lombok.Data;
import org.scm4j.deployer.api.IDeployedProduct;
import org.scm4j.deployer.api.IProductStructure;

import java.net.URL;

@Data
public class DeployedProduct implements IDeployedProduct {

    private URL deploymentUrl;
    private String productVersion;
    private IProductStructure productStructure;

}

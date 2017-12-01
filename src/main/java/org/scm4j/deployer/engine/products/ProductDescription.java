package org.scm4j.deployer.engine.products;

import lombok.Data;

@Data
public class ProductDescription {

    private long deploymentTime;
    private String deploymentPath;
    private String productVersion;

}

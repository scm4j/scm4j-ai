package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(exclude = "deploymentTime")
public class ProductDescription {

    private long deploymentTime;
    private String deploymentPath;
    private String productVersion;

}

package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString
@EqualsAndHashCode(exclude = "deploymentTime")
public class ProductDescription {

	private final String productName;
	private final String deploymentTime;
	private final String deploymentPath;
	private final String productVersion;

}

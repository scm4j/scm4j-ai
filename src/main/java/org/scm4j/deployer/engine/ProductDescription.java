package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@EqualsAndHashCode(exclude = "deploymentTime")
public class ProductDescription {

	private LocalDateTime deploymentTime;
	private String deploymentPath;
	private String productVersion;

}

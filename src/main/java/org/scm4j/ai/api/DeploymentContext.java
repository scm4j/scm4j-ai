package org.scm4j.ai.api;

import lombok.Data;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

@Data
public class DeploymentContext implements IDeploymentContext {

    final private Map<String, File> artifacts;
    final private String mainArtifact;
    final private List<String> deps;
    final private URL deploymentURL;
    final private Map<String,Object> params;

}

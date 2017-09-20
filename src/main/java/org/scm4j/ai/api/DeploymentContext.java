package org.scm4j.ai.api;

import lombok.Data;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

@Data
public class DeploymentContext implements IDeploymentContext {

    private Map<String, File> artifacts;
    private String mainArtifact;
    private List<String> deps;
    private URL deploymentURL;
    private Map<String,Object> params;

}

package org.scm4j.ai.api;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

public interface IDeploymentContext {

    Map<String, File> getArtifacts();
    String getMainArtifact();
    List<String> getDeps();
    URL getDeploymentURL();
    Map<String, Object> getParams();

}

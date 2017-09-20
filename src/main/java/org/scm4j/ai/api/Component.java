package org.scm4j.ai.api;

import lombok.Data;

@Data
public class Component implements IComponent {

    final private String artifactCoords;
    final private IInstallationProcedure installationProcedure;

}

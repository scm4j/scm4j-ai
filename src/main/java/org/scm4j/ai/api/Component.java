package org.scm4j.ai.api;

public class Component implements IComponent {

    private String artifactCoords;
    private IInstallationProcedure iInstallationProcedure;

    public Component(String artifactCoords, IInstallationProcedure iInstallationProcedure) {
        this.artifactCoords = artifactCoords;
        this.iInstallationProcedure = iInstallationProcedure;
    }

    @Override
    public String getArtifactCoords() {
        return artifactCoords;
    }

    @Override
    public IInstallationProcedure getInstalationProcedure() {
        return iInstallationProcedure;
    }
}

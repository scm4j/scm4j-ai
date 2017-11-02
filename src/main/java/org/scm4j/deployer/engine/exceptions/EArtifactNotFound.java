package org.scm4j.deployer.engine.exceptions;

public class EArtifactNotFound extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EArtifactNotFound(String message) {
        super(message);
    }
}

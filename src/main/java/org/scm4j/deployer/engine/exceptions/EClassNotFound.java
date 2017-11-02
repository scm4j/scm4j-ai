package org.scm4j.deployer.engine.exceptions;

public class EClassNotFound extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EClassNotFound(String message) {
        super(message);
    }
}

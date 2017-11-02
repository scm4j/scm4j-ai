package org.scm4j.deployer.engine.exceptions;

public class EProductNotFound extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EProductNotFound(String message) {
        super(message);
    }
}

package org.scm4j.deployer.engine.exceptions;

public class EProductListEntryNotFound extends RuntimeException {

    public EProductListEntryNotFound(String message) {
        super(message);
        printStackTrace();
    }

    private static final long serialVersionUID = 1L;
}

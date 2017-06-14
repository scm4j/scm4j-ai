package org.scm4j.ai.exceptions;

import java.io.IOException;

public class ENoConfig extends RuntimeException {

	public ENoConfig(IOException e) {
		super(e);
	}

	public ENoConfig(String message) {
		super(message);
	}

	private static final long serialVersionUID = 1L;

}

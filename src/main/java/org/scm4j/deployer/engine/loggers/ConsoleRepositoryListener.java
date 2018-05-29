package org.scm4j.deployer.engine.loggers;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

@Slf4j
public class ConsoleRepositoryListener
		extends AbstractRepositoryListener {

	public void artifactInstalling(RepositoryEvent event) {
		if (!event.getArtifact().getExtension().equals("pom"))
			log.debug("Installing " + event.getArtifact() + " to " + event.getFile());
	}

	public void artifactDownloading(RepositoryEvent event) {
		if (!event.getArtifact().getExtension().equals("pom"))
			log.info("Downloading artifact " + event.getArtifact() + " from " + event.getRepository());
	}

}


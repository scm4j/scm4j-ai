package org.scm4j.deployer.engine.loggers;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

@Slf4j
public class ConsoleRepositoryListener
        extends AbstractRepositoryListener {

    public void artifactInstalled(RepositoryEvent event) {
        log.info("Installed " + event.getArtifact() + " to " + event.getFile());
    }

    public void artifactInstalling(RepositoryEvent event) {
        log.info("Installing " + event.getArtifact() + " to " + event.getFile());
    }

    public void artifactResolved(RepositoryEvent event) {
        log.info("Resolved artifact " + event.getArtifact() + " from " + event.getRepository());
    }

    public void artifactDownloading(RepositoryEvent event) {
        log.info("Downloading artifact " + event.getArtifact() + " from " + event.getRepository());
    }

    public void artifactDownloaded(RepositoryEvent event) {
        log.info("Downloaded artifact " + event.getArtifact() + " from " + event.getRepository());
    }

    public void artifactResolving(RepositoryEvent event) {
        log.trace("Resolving artifact " + event.getArtifact());
    }

    public void metadataInstalled(RepositoryEvent event) {
        log.info("Installed " + event.getMetadata() + " to " + event.getFile());
    }

    public void metadataInstalling(RepositoryEvent event) {
        log.info("Installing " + event.getMetadata() + " to " + event.getFile());
    }

}


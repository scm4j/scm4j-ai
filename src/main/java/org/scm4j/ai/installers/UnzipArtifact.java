package org.scm4j.ai.installers;

import lombok.Data;
import org.scm4j.ai.Utils;
import org.scm4j.ai.api.IDeployer;

import java.io.File;


@Data
public class UnzipArtifact implements IDeployer {

    private File outputFile;
    private File zipFile;

    public UnzipArtifact(File outputFile, File zipFile) {
        this.outputFile = outputFile;
        this.zipFile = zipFile;
    }

    @Override
    public void deploy() {
        Utils.unzip(outputFile, zipFile);
    }

    @Override
    public void unDeploy() {

    }

    @Override
    public boolean canDeploy() {
        return false;
    }

    @Override
    public boolean checkIntegrity() {
        return false;
    }
}

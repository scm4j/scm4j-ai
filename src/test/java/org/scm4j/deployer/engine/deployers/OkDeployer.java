package org.scm4j.deployer.engine.deployers;

import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.util.Map;

public class OkDeployer implements IComponentDeployer {
    @Override
    public DeploymentResult deploy() {
        return DeploymentResult.OK;
    }

    @Override
    public DeploymentResult undeploy() {
        return null;
    }

    @Override
    public DeploymentResult stop() {
        return null;
    }

    @Override
    public DeploymentResult start() {
        return null;
    }

    @Override
    public void init(IDeploymentContext depCtx, Map<String, Object> params) {

    }
}

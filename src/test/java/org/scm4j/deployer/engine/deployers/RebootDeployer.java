package org.scm4j.deployer.engine.deployers;

import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

import java.util.Map;

public class RebootDeployer implements IComponentDeployer {
    @Override
    public DeploymentResult deploy() {
        return DeploymentResult.NEED_REBOOT;
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

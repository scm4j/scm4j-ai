package org.scm4j.deployer.engine.deployers;

import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeploymentContext;

public class RebootDeployer implements IComponentDeployer {

    private static int count = 0;

    public static int getCount() {
        return count;
    }

    public static void setCount(int count) {
        RebootDeployer.count = count;
    }

    @Override
    public DeploymentResult deploy() {
        count++;
        return DeploymentResult.NEED_REBOOT;
    }

    @Override
    public DeploymentResult undeploy() {
        count--;
        return DeploymentResult.OK;
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
    public void init(IDeploymentContext depCtx) {

    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}

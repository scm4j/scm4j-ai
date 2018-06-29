package org.scm4j.deployer.engine.deployers;

import org.scm4j.deployer.api.DeploymentResult;

public class RebootDeployer extends OkDeployer {

	private static int count = 0;

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
}

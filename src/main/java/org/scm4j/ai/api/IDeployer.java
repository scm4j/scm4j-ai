package org.scm4j.ai.api;

public interface IDeployer {

	void deploy();

	void unDeploy();

	boolean canDeploy();

	boolean checkIntegrity();

}

package org.scm4j.ai;

public interface IDeployer {

	public void deploy();

	public void unDeploy();

	public boolean canDeploy();

	public boolean checkIntegrity();

}

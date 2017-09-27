package org.scm4j.deployer.engine;

import org.apache.commons.cli.*;
import org.scm4j.deployer.api.IProductDeployer;

import java.io.File;

public class Cli {

	public static void main(String args[]) throws Exception {
		Options options = new Options();
		options.addOption(Option.builder("install")
				.hasArg(true)
				.optionalArg(false)
				.required(false)
				.build());
		options.addOption(Option.builder("upgrade")
				.hasArg(true)
				.optionalArg(false)
				.required(false)
				.build());
		options.addOption(Option.builder("uninstall")
				.hasArg(true)
				.optionalArg(false)
				.required(false)
				.build());

		CommandLineParser cmdLineParser = new DefaultParser();

		CommandLine commandLine = cmdLineParser.parse(options, args);

		// TODO: add working folder providing
		IProductDeployer ai = new DeployerEngine(new File(""),"");
		if (commandLine.hasOption("install")) {
			ai.deploy(commandLine.getOptionValue("install"));
		} else if (commandLine.hasOption("upgrade")) {
			ai.upgrade(commandLine.getOptionValue("upgrade"));
		} else if (commandLine.hasOption("uninstall")) {
			ai.undeploy(commandLine.getOptionValue("uninstall"));
		}
	}

}

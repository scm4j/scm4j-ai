package org.scm4j.deployer.engine;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.scm4j.deployer.installers.IAI;

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
		IAI ai = new AI(new File(""),"");
		if (commandLine.hasOption("install")) {
			ai.install(commandLine.getOptionValue("install"));
		} else if (commandLine.hasOption("upgrade")) {
			ai.upgrade(commandLine.getOptionValue("upgrade"));
		} else if (commandLine.hasOption("uninstall")) {
			ai.uninstall(commandLine.getOptionValue("uninstall"));
		}
	}

}

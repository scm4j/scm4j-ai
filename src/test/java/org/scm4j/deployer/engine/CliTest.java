package org.scm4j.deployer.engine;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

public class CliTest {
	
	@Test
	public void testMain() throws ParseException {
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
		options.addOption(Option.builder("upgrade")
				.hasArg(true)
				.optionalArg(false)
				.required(false)
				.build());
		
		CommandLineParser cmdLineParser = new DefaultParser();
		
		CommandLine commandLine = cmdLineParser.parse(options, new String[] {"test.exe", "-install", "-path"});
		commandLine.getOptionValue("path");
		commandLine.hasOption("install");
		
	}

}

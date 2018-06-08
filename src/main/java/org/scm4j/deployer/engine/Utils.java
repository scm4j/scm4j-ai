package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;
import org.scm4j.deployer.engine.loggers.RepositoryLogger;
import org.scm4j.deployer.engine.loggers.TransferListener;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class Utils {

	private Utils() {
	}

	public static String coordsToFileName(String artifactId, String version, String extension) {
		return artifactId + "-" + version + StringUtils.prependIfMissing(extension, ".");
	}

	public static String coordsToFolderStructure(String groupId, String artifactId) {
		return new File(groupId.replace(".", File.separator), artifactId).getPath();
	}

	public static String coordsToFolderStructure(String groupId, String artifactId, String version) {
		return new File(coordsToFolderStructure(groupId, artifactId), version).getPath();
	}

	public static String coordsToRelativeFilePath(String groupId, String artifactId, String version, String extension,
												  String classifier) {
		if (classifier != null && !classifier.equals(""))
			classifier = "-" + classifier;
		else
			classifier = "";
		return new File(coordsToFolderStructure(groupId, artifactId, version),
				coordsToFileName(artifactId, version + classifier,
						StringUtils.prependIfMissing(extension, "."))).getPath();
	}

	public static RepositorySystem newRepositorySystem() {
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);
		locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

		locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
			@Override
			public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
				exception.printStackTrace();
			}
		});
		return locator.getService(RepositorySystem.class);
	}

	public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, File repository) {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		DefaultArtifactTypeRegistry stereotypes = new DefaultArtifactTypeRegistry();
		stereotypes.add(new DefaultArtifactType("pom"));
		stereotypes.add(new DefaultArtifactType("maven-plugin", "jar", "", "java"));
		stereotypes.add(new DefaultArtifactType("zip"));
		stereotypes.add(new DefaultArtifactType("exe"));
		stereotypes.add(new DefaultArtifactType("javadoc", "jar", "javadoc", "java"));
		stereotypes.add(new DefaultArtifactType("java-source", "jar", "sources", "java", false, false));
		stereotypes.add(new DefaultArtifactType("war", "war", "", "java", false, true));
		session.setArtifactTypeRegistry(stereotypes);
		LocalRepository localRepo = new LocalRepository(repository);
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
		session.setTransferListener(new TransferListener());
		session.setRepositoryListener(new RepositoryLogger());
		return session;
	}

	@SneakyThrows
	public static String getExportedClassName(File jarFile) {
		@Cleanup
		JarFile jarfile = new JarFile(jarFile);
		Manifest manifest = jarfile.getManifest();
		Attributes attrs = manifest.getMainAttributes();
		for (Object obj : attrs.keySet()) {
			Attributes.Name attrName = (Attributes.Name) obj;
			if (attrName.equals(Attributes.Name.MAIN_CLASS))
				return attrs.getValue(attrName);
		}
		throw new RuntimeException();
	}

	public static Artifact initializeArtifact(Downloader downloader, String artifactId, String version) {
		String groupAndArtifactId = downloader.getProductList().getProducts().getOrDefault(artifactId, "");
		if (groupAndArtifactId.isEmpty()) {
			throw new EProductNotFound("Can't find product in product list");
		}
		String[] arr = groupAndArtifactId.split(":");
		return new DefaultArtifact(arr[0], arr[1], "jar", version);
	}

	@SneakyThrows
	public static void writeYaml(Map entry, File output) {
		@Cleanup
		FileWriter writer = new FileWriter(output);
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		Yaml yaml = new Yaml(options);
		String yamlOutput = yaml.dump(entry);
		writer.write(yamlOutput);
	}

	@SneakyThrows
	public static Map readYml(File input) throws NullPointerException {
		if (input.exists()) {
			@Cleanup
			FileReader reader = new FileReader(input);
			Yaml yaml = new Yaml();
			return yaml.loadAs(reader, HashMap.class);
		} else {
			return new HashMap<>();
		}
	}
}

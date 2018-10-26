package org.scm4j.deployer.engine;

import com.google.gson.GsonBuilder;
import lombok.Cleanup;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
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
import org.scm4j.deployer.api.ProductInfo;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;
import org.scm4j.deployer.engine.loggers.RepositoryLogger;
import org.scm4j.deployer.engine.loggers.TransferListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.lang.reflect.Type;
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
		String groupAndArtifactId = downloader.getProductList().getProducts().getOrDefault(artifactId,
				new ProductInfo("", false)).getArtifactId();
		if (groupAndArtifactId.isEmpty()) {
			throw new EProductNotFound("Can't find product in product list");
		}
		String[] arr = groupAndArtifactId.split(":");
		return new DefaultArtifact(arr[0], arr[1], "jar", version);
	}

	@SneakyThrows
	public static void writeJson(Object obj, File file) {
		@Cleanup
		FileWriter writer = new FileWriter(file);
		new GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
	}

	@SneakyThrows
	public static <V> Map<String, V> readJson(File file, Type type) {
		try {
			return new GsonBuilder().setPrettyPrinting().create()
					.fromJson(FileUtils.readFileToString(file, "UTF-8"), type);
		} catch (FileNotFoundException e) {
			return new HashMap<>();
		}
	}

}

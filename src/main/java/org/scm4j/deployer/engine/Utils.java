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
import org.scm4j.deployer.engine.loggers.ConsoleRepositoryListener;
import org.scm4j.deployer.engine.loggers.ConsoleTransferListener;
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
import java.util.stream.Collectors;

public class Utils {

    public static String coordsToString(String groupId, String artifactId, String version, String extension) {
        return coordsToString(groupId, artifactId) + ":" + version + ":" + StringUtils.prependIfMissing(extension, ".");
    }

    public static String coordsToString(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
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

    public static String coordsToRelativeFilePath(String groupId, String artifactId, String version, String extension) {
        return new File(coordsToFolderStructure(groupId, artifactId, version),
                coordsToFileName(artifactId, version, StringUtils.prependIfMissing(extension, "."))).getPath();
    }

    public static String coordsToUrlStructure(String groupId, String artifactId) {
        return coordsToString(groupId, artifactId).replace(".", "/").replace(":", "/");
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
        session.setTransferListener(new ConsoleTransferListener());
        session.setRepositoryListener(new ConsoleRepositoryListener());
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
            if (attrName.equals(Attributes.Name.MAIN_CLASS)) {
                return attrs.getValue(attrName);
            }
        }
        throw new RuntimeException();
    }

    public static String getGroupId(Downloader downloader, String artifactId) {
        String groupAndArtifactID = downloader.getProductList().getProducts().keySet().stream()
                .filter(s -> s.contains(artifactId))
                .limit(1)
                .collect(Collectors.toList())
                .get(0);
        return StringUtils.substringBefore(groupAndArtifactID, ":");
    }

    public static Artifact initializeArtifact(Downloader downloader, String artifactId, String version) {
        String groupId = getGroupId(downloader, artifactId);
        return new DefaultArtifact(groupId, artifactId, "jar", version);
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

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static Map readYml(File input) {
        if (input.exists()) {
            @Cleanup
            FileReader reader = new FileReader(input);
            Yaml yaml = new Yaml();
            return (Map) yaml.load(reader);
        } else {
            return new HashMap<>();
        }
    }

    public static StringBuilder productName(String artifactId, String version) {
        return new StringBuilder().append(artifactId).append("-").append(version);
    }
}

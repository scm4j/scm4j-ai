package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.scm4j.deployer.engine.exceptions.EClassNotFound;
import org.scm4j.deployer.engine.loggers.ConsoleRepositoryListener;
import org.scm4j.deployer.engine.loggers.ConsoleTransferListener;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class Utils {

    //TODO change Utils for work with Coords
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
        stereotypes.add( new DefaultArtifactType( "pom" ) );
        stereotypes.add( new DefaultArtifactType( "maven-plugin", "jar", "", "java" ) );
        stereotypes.add(new DefaultArtifactType("zip"));
        stereotypes.add(new DefaultArtifactType("exe"));
        stereotypes.add( new DefaultArtifactType( "javadoc", "jar", "javadoc", "java" ) );
        stereotypes.add( new DefaultArtifactType( "java-source", "jar", "sources", "java", false, false ) );
        stereotypes.add( new DefaultArtifactType( "war", "war", "", "java", false, true ) );
        session.setArtifactTypeRegistry( stereotypes );
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

    @SneakyThrows
    public static Object createClassFromJar(File jarFile, String className) {
        @Cleanup
        URLClassLoader loader = URLClassLoader.newInstance(new URL[] {jarFile.toURI().toURL()});
        Class<?> clazz;
        try {
            clazz = loader.loadClass(className);
        } catch (ClassNotFoundException e) {
            clazz = loader.loadClass(findClassName(jarFile, className));
        }
        return clazz.newInstance();
    }

    @SneakyThrows
    private static String findClassName(File jarFile, String className) {
        String fullClassName = null;
        JarFile file = new JarFile(jarFile);
        Enumeration<JarEntry> entries = file.entries();
        while(entries.hasMoreElements()) {
            JarEntry je = entries.nextElement();
            if(!je.isDirectory() && je.getName().endsWith(".class") && (je.getName().contains(className))) {
                fullClassName = (je.getName().substring(0,je.getName().length()-6)
                        .replace("/", "."));
                break;
            }
        }
        if(fullClassName == null) {
            throw new EClassNotFound(className + " class not found");
        }
        return fullClassName;
    }

    public static String getGroupId(DeployerRunner runner, String artifactId) {
        String groupAndArtifactID = runner.getProductList().getProducts().stream()
                .filter(s -> s.contains(artifactId))
                .limit(1)
                .collect(Collectors.toList())
                .get(0);
        return StringUtils.substringBefore(groupAndArtifactID, ":");
    }

    @SneakyThrows
    public static void writeYaml(Map<String, Set<String>> entry, File output) {
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
    public static Map<String, Set<String>> readYml(File input) {
        if (input.exists()) {
            @Cleanup
            FileReader reader = new FileReader(input);
            Yaml yaml = new Yaml();
            return (Map<String, Set<String>>) yaml.load(reader);
        } else {
            return new HashMap<>();
        }
    }
}

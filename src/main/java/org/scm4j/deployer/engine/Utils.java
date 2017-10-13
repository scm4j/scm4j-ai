package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.Attributes;
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
    public static Object loadClassFromJar(File jarFile, String className) {
        @Cleanup
        URLClassLoader loader = URLClassLoader.newInstance(new URL[] {jarFile.toURI().toURL()});
        Class<?> loadedClass = loader.loadClass(className);
        return loadedClass.newInstance();
    }

    public static String getGroupId(DeployerRunner runner, String artifactId) {
        String groupAndArtifactID = runner.getProductList().getProducts().stream()
                .filter(s -> s.contains(artifactId))
                .limit(1)
                .collect(Collectors.toList())
                .get(0);
        return StringUtils.substringBefore(groupAndArtifactID, ":");
    }
}

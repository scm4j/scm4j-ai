package org.scm4j.ai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.scm4j.ai.api.*;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.EProductNotFound;
import org.scm4j.ai.installers.InstallerFactory;

@Data
public class AIRunner {

    private static final String REPOSITORY_FOLDER_NAME = "repository";
    private File tmpRepository;
    private File repository;
    private InstallerFactory installerFactory;
    private RepositorySystem system;
    private RepositorySystemSession session;
    private ProductList productList;

    public void setInstallerFactory(InstallerFactory installerFactory) {
        this.installerFactory = installerFactory;
    }

    @SneakyThrows
    public AIRunner(File workingFolder, String productListArtifactoryUrl, String userName, String password) {
        repository = new File(workingFolder, REPOSITORY_FOLDER_NAME);
        productList = new ProductList(repository);

        if (!repository.exists()) {
            Files.createDirectory(repository.toPath());
        }
        productList.downloadProductList(productListArtifactoryUrl, userName, password);

        tmpRepository = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-test");
        system = Utils.newRepositorySystem();
    }

    @SneakyThrows
    public List<String> getProductVersions(String groupId, String artifactId) {
        if (!productList.hasProduct(groupId, artifactId)) {
            throw new EProductNotFound();
        }
        MetadataXpp3Reader reader = new MetadataXpp3Reader();
        try (InputStream is = productList.getProductListReader().getProductMetaDataURL(groupId, artifactId).openStream()) {
            Metadata meta = reader.read(is);
            Versioning vers = meta.getVersioning();
            return vers.getVersions();
        }
    }

    public void install(File productDir) {
        installerFactory.getInstaller(productDir).install();
    }

    public File get(String groupId, String artifactId, String version, String extension) throws EArtifactNotFound {
        File res = queryLocal(groupId, artifactId, version, extension);
        if (res == null) {
            res = download(groupId, artifactId, version, extension);
            if (res == null) {
                throw new EArtifactNotFound(Utils.coordsToString(groupId, artifactId, version, extension)
                        + " is not found in all known repositories");
            }
        }
        return res;
    }

    public File queryLocal(String groupId, String artifactId, String version, String extension) {
        String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
        File res = new File(repository, fileRelativePath);
        if (!res.exists()) {
            return null;
        }
        return res;
    }

    //TODO write product-list and product metadata in local repo
    public File download(String groupId, String artifactId, String version, String extension) {
        String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
        File res = new File(repository, fileRelativePath);
        File temp = new File(tmpRepository, fileRelativePath);
        for (ArtifactoryReader repo : productList.getRepos()) {
            try {
                if (!productList.getProducts().contains(Utils.coordsToString(groupId, artifactId))
                        || !getProductVersions(groupId, artifactId).contains(version)) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }

            try {
                File parent = temp.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                temp.createNewFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            //TODO Divide exceptions on write exception and read exception
            //TODO delete jar if can't download pom
            try (FileOutputStream out = new FileOutputStream(temp);
                 InputStream in = repo.getContentStream(groupId, artifactId, version, extension)) {
                IOUtils.copy(in, out);
            } catch (Exception e1) {
                continue;
            }
            try {
                List<Artifact> artifacts = getComponents(temp);
                Artifact productArtifact = new DefaultArtifact(groupId, artifactId, StringUtils.substringAfter(extension, "."),
                        version);
                artifacts.add(productArtifact);
                artifacts = downloadComponents(artifacts);
                deployComponents(artifacts);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return res;
        }
        return null;
    }

    public List<Artifact> downloadComponents(List<Artifact> artifacts) throws DependencyResolutionException {
        List<Artifact> components = new ArrayList<>();
        session = Utils.newRepositorySystemSession(system, tmpRepository);
        CollectRequest collectRequest = new CollectRequest();
        productList.getRepos().forEach(artifactoryReader -> collectRequest.addRepository
                (new RemoteRepository.Builder("", "default", artifactoryReader.toString()).build()));
        DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
        for(Artifact artifact : artifacts) {
            collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter);
            List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
            artifactResults.forEach((artifactResult) -> components.add(artifactResult.getArtifact()));
        }
        return components;
    }

    private void deployComponents(List<Artifact> artifacts) throws InstallationException, ArtifactDescriptorException {
        session = Utils.newRepositorySystemSession(system, repository);
        InstallRequest installRequest = new InstallRequest();
        for (Artifact artifact : artifacts) {
            Artifact pomArtifact = new SubArtifact(artifact, "", "pom");
            pomArtifact = pomArtifact.setFile(new File(tmpRepository, Utils.coordsToRelativeFilePath(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getVersion(), ".pom")));
            installRequest.addArtifact(artifact).addArtifact(pomArtifact);
        }
        system.install(session, installRequest);
    }

    private List<Artifact> getComponents(File productFile) throws Exception {
        return getProductStructure(productFile).getComponents().stream()
                .map(IComponent::getArtifactCoords)
                .map(DefaultArtifact::new)
                .collect(Collectors.toList());
    }

    public IProductStructure getProductStructure(File productFile) throws Exception {
        String mainClassName = Utils.getExportedClassName(productFile);
        @Cleanup
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{productFile.toURI().toURL()});
        Class<?> loadedClass = Class.forName(mainClassName, true, classLoader);
        Object obj = loadedClass.newInstance();
        if(obj instanceof IProduct) {
            IProduct product = (IProduct) obj;
            return product.getProductStructure();
        } else {
            throw new RuntimeException();
        }
    }
}

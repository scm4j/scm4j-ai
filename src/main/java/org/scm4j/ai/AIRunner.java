package org.scm4j.ai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
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
import org.scm4j.ai.api.IComponent;
import org.scm4j.ai.api.IInstaller;
import org.scm4j.ai.api.IProductStructure;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.exceptions.EProductNotFound;
import org.scm4j.ai.installers.InstallerFactory;
import org.yaml.snakeyaml.Yaml;

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
    public List<String> getProductVersions(String groupId, String artifactId){
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

    public File download(String groupId, String artifactId, String version, String extension) {
        String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
        File res = new File(repository, fileRelativePath);
        File pom = new File(res.getParent(), Utils.coordsToFileName(artifactId, version, ArtifactoryReader.POM_FILE_EXTENTION));
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
                File parent = res.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                res.createNewFile();
                pom.createNewFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            //TODO Divide exceptions on write exception and read exception
            //TODO delete jar if can't download pom
            try (FileOutputStream out = new FileOutputStream(res);
                 InputStream in = repo.getContentStream(groupId, artifactId, version, extension)) {
                IOUtils.copy(in, out);
            } catch (Exception e1) {
                continue;
            }
            try (FileOutputStream outPom = new FileOutputStream(pom);
                 InputStream inPom = repo.getContentStream(groupId, artifactId, version, ArtifactoryReader.POM_FILE_EXTENTION)) {
                IOUtils.copy(inPom, outPom);
            } catch (Exception e2) {
                continue;
            }
            try {
                List<Artifact> artifacts = getComponents(groupId, artifactId, version);
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
        session = Utils.newRepositorySystemSession(system, tmpRepository);
        CollectRequest collectRequest = new CollectRequest();
        productList.getRepos().forEach(artifactoryReader -> collectRequest.addRepository
                (new RemoteRepository.Builder("","default",artifactoryReader.toString()).build()));
        DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
        artifacts.forEach((artifact -> collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE))));
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter);
        List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
        artifacts.clear();
        artifactResults.forEach((artifactResult) -> artifacts.add(artifactResult.getArtifact()));
        return artifacts;
    }

    public void deployComponents(List<Artifact> artifacts) throws InstallationException, ArtifactDescriptorException {
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

//    public List<Artifact> getComponents(String groupId, String artifactId, String version) throws Exception {
//        Map<String, String> product;
//        URL productUrl = productList.getProductListReader().getProductUrl(groupId, artifactId, version, ".jar");
//        @Cleanup
//        InputStream is = productList.getProductListReader().getContentStream(productUrl);
//        Yaml yaml = new Yaml();
//        product = yaml.loadAs(is, HashMap.class);
//        Set<String> components = product.keySet();
//        return components.stream().map(DefaultArtifact::new).collect(Collectors.toList());
//    }

    public List<Artifact> getComponents(String groupId, String artifactId, String version) throws Exception {
        File productFile = new File(Utils.coordsToRelativeFilePath(groupId, artifactId, version, ".jar"));
        List<IComponent> components = getProductStructure(productFile).getComponents();
        return components.stream()
                .map(IComponent::getArtifactCoords)
                .map(DefaultArtifact::new)
                .collect(Collectors.toList());
    }

    public IProductStructure getProductStructure(File productFile) {
        String mainClassName = Utils.getExportedClassName(productFile);
        try {
            Class<?> productStructureClass = Class.forName(mainClassName);
            Constructor<?> constructor = productStructureClass.getConstructor();
            Object result = constructor.newInstance();
            IProductStructure productStructure;
            if (result.getClass().isAssignableFrom(IProductStructure.class)) {
                productStructure = (IProductStructure) result;
            } else {
                throw new RuntimeException("Provided " + mainClassName + " does not implements IInstaller");
            }
            return productStructure;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(mainClassName + " class not found");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(mainClassName + " class has no constructor");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.scm4j.commons.Coords;
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.exceptions.EArtifactNotFound;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class DeployerRunner {

    private static final String REPOSITORY_FOLDER_NAME = "repository";
    private File tmpRepository;
    private File repository;
    private RepositorySystem system;
    private RepositorySystemSession session;
    private ProductList productList;
    private List<DeploymentContext> depCtx;

    private static final String DEFAULT_DEPLOYMENT_URL = "file://localhost/C:/tools/unTILL";

    @SneakyThrows
    public DeployerRunner(File workingFolder, String productListArtifactoryUrl) {
        repository = new File(workingFolder, REPOSITORY_FOLDER_NAME);
        if (!repository.exists()) {
            Files.createDirectory(repository.toPath());
        }

        ArtifactoryReader productListReader = ArtifactoryReader.getByUrl(productListArtifactoryUrl);
        productList = new ProductList(repository, productListReader);
        productList.readFromProductList();

        tmpRepository = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-tmp");
        system = Utils.newRepositorySystem();
    }

    public File get(String groupId, String artifactId, String version, String extension) throws EArtifactNotFound {
        String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
        File res = new File(repository, fileRelativePath);
        if (!res.exists()) {
            res = download(groupId, artifactId, version, extension);
            if (res == null) {
                throw new EArtifactNotFound(Utils.coordsToString(groupId, artifactId, version, extension)
                        + " is not found in all known repositories");
            }
        }
        return res;
    }

    @SneakyThrows
    public File download(String groupId, String artifactId, String version, String extension) {
        String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
        File res = new File(repository, fileRelativePath);
        File temp = new File(tmpRepository, fileRelativePath);
        for (ArtifactoryReader repo : productList.getRepos()) {
            try {
                if (!productList.getProducts().contains(Utils.coordsToString(groupId, artifactId))
                        || !productList.getProductVersions(groupId, artifactId).contains(version)) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }

            File parent = temp.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            temp.createNewFile();

            //TODO Divide exceptions on write exception and read exception
            try (FileOutputStream out = new FileOutputStream(temp);
                 InputStream in = repo.getContentStream(groupId, artifactId, version, extension)) {
                IOUtils.copy(in, out);
            } catch (Exception e1) {
                continue;
            }

            depCtx = new ArrayList<>();

            List<Artifact> artifacts = getComponents(temp);
            Artifact productArtifact = new DefaultArtifact(groupId, artifactId, extension,version);
            artifacts.add(productArtifact);
            artifacts = downloadComponents(artifacts);
            saveComponents(artifacts);
            File localMetadataFolder = new File(repository, Utils.coordsToFolderStructure(groupId, artifactId));
            File localMetadata = new File(localMetadataFolder, ArtifactoryReader.LOCAL_METADATA_FILE_NAME);
            localMetadata.renameTo(new File(localMetadataFolder, ArtifactoryReader.METADATA_FILE_NAME));
            productList.appendLocalRepo();
            productList.markDownloadedProduct(artifactId, version);
            return res;
        }
        return null;
    }

    @SneakyThrows
    public List<Artifact> downloadComponents(List<Artifact> artifacts) {
        List<Artifact> components = new ArrayList<>();
        session = Utils.newRepositorySystemSession(system, tmpRepository);
        CollectRequest collectRequest = new CollectRequest();
        productList.getRepos().forEach(artifactoryReader -> collectRequest.addRepository
                (new RemoteRepository.Builder("", "default", artifactoryReader.toString()).build()));
        DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
        for (Artifact artifact : artifacts) {
            DeploymentContext context = new DeploymentContext(artifact.getArtifactId());
            depCtx.add(context);
            collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter);
            List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
            artifactResults.forEach((artifactResult) -> components.add(artifactResult.getArtifact()));
            Map<Coords, File> arts = components.stream()
                    .map(component -> component.setFile(new File(repository, Utils.coordsToRelativeFilePath(component.getGroupId(),
                            component.getArtifactId(), component.getVersion(),
                            component.getExtension()))))
                    .collect(Collectors.toMap(component -> new Coords(Utils.coordsToString(component.getGroupId(),
                            component.getArtifactId(), component.getVersion(),component.getExtension())), Artifact::getFile ));
            context.setArtifacts(arts);
        }
        return components;
    }

    @SneakyThrows
    private void saveComponents(List<Artifact> artifacts) {
        session = Utils.newRepositorySystemSession(system, repository);
        InstallRequest installRequest = new InstallRequest();
        for (Artifact artifact : artifacts) {
            Artifact pomArtifact = new SubArtifact(artifact, "", "pom");
            pomArtifact = pomArtifact.setFile(new File(tmpRepository, Utils.coordsToRelativeFilePath(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getVersion(), ".pom")));
            installRequest.addArtifact(artifact).addArtifact(pomArtifact);
        }
        system.install(session, installRequest);
        FileUtils.deleteDirectory(tmpRepository);
    }

    @SneakyThrows
    private IDeploymentContext collectDeploymentContext(IComponent component) {
        Coords coords = component.getArtifactCoords();
        DeploymentContext deploymentContext = new DeploymentContext(coords.toString());
        deploymentContext.setDeploymentURL(new URL(DEFAULT_DEPLOYMENT_URL));
        return null;
    }

    private List<Artifact> getComponents(File productFile) {
        return getProductStructure(productFile).getComponents().stream()
                .map(IComponent::getArtifactCoords)
                .map(Coords::toString)
                .map(DefaultArtifact::new)
                .collect(Collectors.toList());
    }

    public IProductStructure getProductStructure(File productFile) {
        String mainClassName = Utils.getExportedClassName(productFile);
        Object obj = Utils.loadClassFromJar(productFile, mainClassName);
        if (obj instanceof IProduct) {
            IProduct product = (IProduct) obj;
            return product.getProductStructure();
        } else {
            throw new RuntimeException();
        }
    }

}

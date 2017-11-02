package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
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
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.scm4j.deployer.api.DeploymentContext;
import org.scm4j.deployer.api.IComponent;
import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.engine.exceptions.EArtifactNotFound;
import org.scm4j.deployer.engine.exceptions.EProductListEntryNotFound;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Data
public class DeployerRunner {

    private static final String REPOSITORY_FOLDER_NAME = "repository";
    private static final File TMP_REPOSITORY = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-tmp");
    private static final String DEFAULT_DEPLOYMENT_URL = "file://localhost/C:/tools/";
    private final Map<String, DeploymentContext> depCtx = new HashMap<>();
    private final ProductList productList;
    private final File workingRepository;
    private final File portableRepository;
    private RepositorySystem system;
    private RepositorySystemSession session;

    @SneakyThrows
    public DeployerRunner(File portableFolder, File workingFolder, String productListArtifactoryUrl) {
        this.workingRepository = new File(workingFolder, REPOSITORY_FOLDER_NAME);
        this.portableRepository = new File(portableFolder, REPOSITORY_FOLDER_NAME);
        if (!portableRepository.exists())
            portableRepository.mkdirs();
        ArtifactoryReader productListReader = ArtifactoryReader.getByUrl(productListArtifactoryUrl);
        this.productList = new ProductList(portableRepository, productListReader);
        this.system = Utils.newRepositorySystem();
    }

    public File get(String groupId, String artifactId, String version, String extension) throws EArtifactNotFound {
        if (productList.getRepos() == null || productList.getProducts() == null) {
            throw new EProductListEntryNotFound("Product list doesn't loaded");
        }
        String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
        File res = new File(portableRepository, fileRelativePath);
        if (res.exists()) {
            List<Artifact> artifacts = getComponents(res);
            artifacts.add(new DefaultArtifact(groupId, artifactId, extension, version));
            artifacts = resolveDependencies(artifacts);
            if (!portableRepository.equals(workingRepository)) {
                if (!workingRepository.exists())
                    workingRepository.mkdirs();
                saveComponents(artifacts, workingRepository);
                res = new File(workingRepository, fileRelativePath);
            }
        } else {
            res = download(groupId, artifactId, version, extension, res);
            if (res == null) {
                throw new EProductNotFound(Utils.coordsToString(groupId, artifactId, version, extension)
                        + " is not found in all known repositories");
            }
        }
        return res;
    }

    @SneakyThrows
    private File download(String groupId, String artifactId, String version, String extension, File productFile) {
        for (ArtifactoryReader repo : productList.getRepos()) {
            try {
                if (!productList.getProducts().keySet().contains(Utils.coordsToString(groupId, artifactId))
                        || !repo.getProductVersions(groupId, artifactId).contains(version)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            List<Artifact> artifacts = resolveDependencies(
                    Collections.singletonList(new DefaultArtifact(groupId, artifactId, extension, version)));
            saveComponents(artifacts, portableRepository);
            artifacts = getComponents(productFile);
            artifacts = resolveDependencies(artifacts);
            saveComponents(artifacts, portableRepository);
            File localMetadataFolder = new File(portableRepository, Utils.coordsToFolderStructure(groupId, artifactId));
            File localMetadata = new File(localMetadataFolder, ArtifactoryReader.LOCAL_METADATA_FILE_NAME);
            localMetadata.renameTo(new File(localMetadataFolder, ArtifactoryReader.METADATA_FILE_NAME));
            return productFile;
        }
        return null;
    }

    @SneakyThrows
    private List<Artifact> resolveDependencies(List<Artifact> artifacts) {
        List<Artifact> components = new ArrayList<>();
        session = Utils.newRepositorySystemSession(system, TMP_REPOSITORY);
        CollectRequest collectRequest = new CollectRequest();
        List<String> urls = productList.getRepos().stream().map(ArtifactoryReader::toString).collect(Collectors.toList());
        urls.add(0, portableRepository.toURI().toURL().toString());
        urls.forEach(url -> collectRequest.addRepository(new RemoteRepository.Builder("", "default", url).build()));
        DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
        for (Artifact artifact : artifacts) {
            collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter);
            try {
                List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
                artifactResults.forEach((artifactResult) -> components.add(artifactResult.getArtifact()));
                depCtx.put(artifact.getArtifactId(), getDeploymentContext(artifact, components));
            } catch (DependencyResolutionException e) {
                throw new RuntimeException();
            }
        }
        return components;
    }

    @SneakyThrows
    private DeploymentContext getDeploymentContext(Artifact artifact, List<Artifact> deps) {
        DeploymentContext context = new DeploymentContext(artifact.getArtifactId());
        context.setDeploymentURL(new URL(new URL(DEFAULT_DEPLOYMENT_URL), artifact.getArtifactId()));
        Map<String, File> arts = deps.stream().collect(Collectors.toMap(Artifact::getArtifactId, Artifact::getFile));
        context.setArtifacts(arts);
        return context;
    }

    @SneakyThrows
    private void saveComponents(List<Artifact> artifacts, File repository) {
        session = Utils.newRepositorySystemSession(system, repository);
        InstallRequest installRequest = new InstallRequest();
        for (Artifact artifact : artifacts) {
            Artifact pomArtifact = new SubArtifact(artifact, "", "pom");
            pomArtifact = pomArtifact.setFile(new File(TMP_REPOSITORY, Utils.coordsToRelativeFilePath(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getVersion(), ".pom")));
            installRequest.addArtifact(artifact).addArtifact(pomArtifact);
        }
        system.install(session, installRequest);
    }

    public List<Artifact> getComponents(File productFile) {
        return getProduct(productFile).getProductStructure().getComponents().stream()
                .map(IComponent::getArtifactCoords)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public IProduct getProduct(File productFile) {
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        File pomfile = new File(productFile.getParent(), productFile.getName().replace("jar", "pom"));
        Model model;
        try {
            @Cleanup
            FileReader reader = new FileReader(pomfile);
            model = mavenreader.read(reader);
        } catch (Exception e) {
            throw new EProductNotFound(productFile.getName() + " pom not found!");
        }
        Optional<org.apache.maven.model.Dependency> apiDep = model.getDependencies().stream()
                .filter(dep -> dep.getArtifactId().equals("scm4j-deployer-api"))
                .findFirst();
        String apiVersion = apiDep.map(org.apache.maven.model.Dependency::getVersion).orElse("");
        if (apiVersion.endsWith("SNAPSHOT") || apiVersion.isEmpty() ||
                IProduct.class.getPackage().isCompatibleWith(apiVersion)) {
            String mainClassName = Utils.getExportedClassName(productFile);
            Object obj = Utils.createClassFromJar(productFile, mainClassName);
            if (obj instanceof IProduct) {
                return (IProduct) obj;
            } else {
                throw new RuntimeException();
            }
        } else {
            throw new EProductNotFound("Can't load " + productFile.getName() + " class to classpath");
        }
    }
}

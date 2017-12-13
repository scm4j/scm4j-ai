package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
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
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.exceptions.EIncompatibleApiVersion;
import org.scm4j.deployer.engine.exceptions.EProductListEntryNotFound;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;

import java.io.File;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Slf4j
class Downloader implements IDownloader {

    private static final String REPOSITORY_FOLDER_NAME = "repository";
    static final File TMP_REPOSITORY = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-tmp");
    private static final String API_NAME = "scm4j-deployer-api";
    private final Map<String, IDeploymentContext> depCtx;
    private final ProductList productList;
    private final File workingRepository;
    private final File portableRepository;
    private RepositorySystem system;
    private RepositorySystemSession session;
    private URLClassLoader loader;
    private IProduct product;

    private Function<Artifact, String> fileSetter = art -> Utils.coordsToRelativeFilePath(art.getGroupId(),
            art.getArtifactId(), art.getVersion(), art.getExtension());

    @SneakyThrows
    Downloader(File portableFolder, File workingFolder, String productListArtifactoryUrl) {
        this.workingRepository = new File(workingFolder, REPOSITORY_FOLDER_NAME);
        this.portableRepository = new File(portableFolder, REPOSITORY_FOLDER_NAME);
        if (!portableRepository.exists())
            portableRepository.mkdirs();
        if (!workingRepository.exists())
            workingRepository.mkdirs();
        ArtifactoryReader productListReader = ArtifactoryReader.getByUrl(productListArtifactoryUrl);
        this.productList = new ProductList(portableRepository, productListReader);
        this.system = Utils.newRepositorySystem();
        this.depCtx = new HashMap<>();
    }

    @Override
    public File getProductFile(String coords) throws EIncompatibleApiVersion {
        Artifact art = new DefaultArtifact(coords);
        return getProductFile(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getExtension());
    }

    @Override
    public File getProductWithDependency(String coords) throws EIncompatibleApiVersion {
        Artifact art = new DefaultArtifact(coords);
        File product = getProductFile(coords);
        String fileRelativePath = Utils.coordsToRelativeFilePath(art.getGroupId(), art.getArtifactId(),
                art.getVersion(), art.getExtension());
        if (product.equals(new File(portableRepository, fileRelativePath)))
            loadProductDependency(portableRepository);
        else
            loadProductDependency(workingRepository);
        return product;
    }

    @Override
    public void loadProductDependency(File repository) {
        List<Artifact> artifacts = componentsToArtifacts();
        artifacts = resolveDependencies(artifacts);
        saveComponents(artifacts, repository);
    }

    private File getProductFile(String groupId, String artifactId, String version, String extension) throws EIncompatibleApiVersion {
        if (productList.getRepos() == null || productList.getProducts() == null) {
            throw new EProductListEntryNotFound("Product list doesn't loaded");
        }
        String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
        File res = new File(portableRepository, fileRelativePath);
        if (res.exists()) {
            List<Artifact> artifacts = resolveDependencies(
                    Collections.singletonList(new DefaultArtifact(groupId, artifactId, extension, version)));
            saveProduct(artifacts, workingRepository, res);
            res = new File(workingRepository, fileRelativePath);
        } else {
            res = downloadProduct(groupId, artifactId, version, extension, res);
            if (res == null) {
                throw new EProductNotFound(Utils.coordsToFileName(groupId, artifactId, version)
                        + " is not found in all known repositories");
            }
        }
        try {
            product.getProductStructure();
            loader.close();
            FileUtils.deleteDirectory(TMP_REPOSITORY);
        } catch (Exception e) {
            throw new RuntimeException();
        }
        return res;
    }

    private void saveProduct(List<Artifact> artifacts, File repository, File productFile) throws EIncompatibleApiVersion {
        artifacts = saveComponents(artifacts, repository);
        instantiateClassLoader(artifacts);
        loadProduct(productFile);
    }

    private File downloadProduct(String groupId, String artifactId, String version, String extension, File productFile) throws EIncompatibleApiVersion {
        Set<String> products = productList.getProducts().keySet();
        if (!products.contains(groupId + ":" + artifactId))
            throw new EProductNotFound("Product not found in product list");
        for (ArtifactoryReader repo : productList.getRepos()) {
            try {
                if (!repo.getProductVersions(groupId, artifactId).contains(version)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            List<Artifact> artifacts = resolveDependencies(
                    Collections.singletonList(new DefaultArtifact(groupId, artifactId, extension, version)));
            saveProduct(artifacts, portableRepository, productFile);
            return productFile;
        }
        return null;
    }

    private void instantiateClassLoader(List<Artifact> artifacts) {
        URL[] urls = artifacts.stream()
                .map(Artifact::getFile)
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (MalformedURLException e) {
                        throw new RuntimeException();
                    }
                })
                .toArray(URL[]::new);
        loader = URLClassLoader.newInstance(urls);
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
            List<Artifact> deps = new ArrayList<>();
            collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter);
            try {
                List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
                artifactResults.forEach(artifactResult -> {
                    Artifact art = artifactResult.getArtifact();
                    art = fileSetter(art, workingRepository, fileSetter);
                    deps.add(art);
                });
                depCtx.put(artifact.getArtifactId(), getDeploymentContext(artifact, deps));
                components.addAll(deps);
            } catch (DependencyResolutionException e) {
                throw new RuntimeException();
            }
        }
        return components;
    }

    private Artifact fileSetter(Artifact art, File repository, Function<Artifact, String> func) {
        art = art.setFile(new File(repository, func.apply(art)));
        return art;
    }

    @SneakyThrows
    private DeploymentContext getDeploymentContext(Artifact artifact, List<Artifact> deps) {
        DeploymentContext context = new DeploymentContext(artifact.getArtifactId());
        Map<String, File> arts = deps.stream().collect(Collectors.toMap(Artifact::getArtifactId, Artifact::getFile));
        context.setArtifacts(arts);
        return context;
    }

    @SneakyThrows
    private List<Artifact> saveComponents(List<Artifact> artifacts, File repository) {
        session = Utils.newRepositorySystemSession(system, repository);
        InstallRequest installRequest = new InstallRequest();
        for (Artifact artifact : artifacts) {
            artifact = fileSetter(artifact, TMP_REPOSITORY, fileSetter);
            Artifact pomArtifact = new SubArtifact(artifact, "", "pom");
            pomArtifact = pomArtifact.setFile(new File(TMP_REPOSITORY, Utils.coordsToRelativeFilePath(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getVersion(), ".pom")));
            installRequest.addArtifact(artifact).addArtifact(pomArtifact);
        }
        system.install(session, installRequest);
        artifacts = artifacts.stream().map(art -> fileSetter(art, repository, fileSetter)).collect(Collectors.toList());
        return artifacts;
    }

    private List<Artifact> componentsToArtifacts() {
        return getComponents().stream()
                .map(IComponent::getArtifactCoords)
                .collect(Collectors.toList());
    }

    private List<IComponent> getComponents() {
        return product.getProductStructure().getComponents();
    }

    @SneakyThrows
    private void loadProduct(File productFile) throws EIncompatibleApiVersion {
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
                .filter(dep -> dep.getArtifactId().equals(API_NAME))
                .findFirst();
        String apiVersion = apiDep.map(org.apache.maven.model.Dependency::getVersion).orElse("");
        if (log.isDebugEnabled()) {
            log.debug("Product API version is " + apiVersion);
            log.debug("Deployer API version is " + IProduct.class.getPackage().getSpecificationVersion());
        }
        if (apiVersion.endsWith("SNAPSHOT") || apiVersion.isEmpty() ||
                IProduct.class.getPackage().isCompatibleWith(apiVersion)) {
            String mainClassName = Utils.getExportedClassName(productFile);
            Object obj;
            try {
                obj = loader.loadClass(mainClassName).getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException();
            }
            if (obj instanceof IProduct) {
                product = (IProduct) obj;
            } else {
                throw new RuntimeException();
            }
        } else {
            if (loader != null)
                loader.close();
            if (TMP_REPOSITORY.exists())
                FileUtils.forceDelete(Downloader.TMP_REPOSITORY);
            throw new EIncompatibleApiVersion("Can't load " + productFile.getName() + " class to classpath");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IDeploymentContext> T getContextByArtifactId(String artifactId) {
        return (T) depCtx.get(artifactId);
    }
}

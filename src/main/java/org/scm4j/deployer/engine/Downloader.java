package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
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
import java.util.stream.Collectors;

@Data
@Slf4j
class Downloader implements IDownloader {

	static final File TMP_REPOSITORY = new File(System.getProperty("java.io.tmpdir"), "scm4j-ai-tmp" +
			UUID.randomUUID());
	private static final String REPOSITORY_FOLDER_NAME = "repository";
	private static final String API_NAME = "scm4j-deployer-api";
	private final Map<String, IDeploymentContext> depCtx;
	private final ProductList productList;
	private final File workingRepository;
	private final File portableRepository;
	private final RepositorySystem system;
	private final String deployerApiVersion;
	private RepositorySystemSession session;
	private URLClassLoader loader;
	private IProduct product;

	@SneakyThrows
	Downloader(File portableFolder, File workingFolder, String productListArtifactoryUrl, String deployerApiVersion) {
		this.deployerApiVersion = deployerApiVersion;
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

	private static Artifact fileSetter(Artifact art, File repository) {
		art = art.setFile(new File(repository, Utils.coordsToRelativeFilePath(art.getGroupId(),
				art.getArtifactId(), art.getVersion(), art.getExtension())));
		return art;
	}

	@SneakyThrows
	private static DeploymentContext getDeploymentContext(Artifact artifact, List<Artifact> deps) {
		DeploymentContext context = new DeploymentContext(artifact.getArtifactId());
		Map<String, File> arts = deps.stream().collect(Collectors.toMap(Artifact::getArtifactId, Artifact::getFile));
		context.setArtifacts(arts);
		return context;
	}

	private static boolean compareApiVersions(String deployerApiVersion, String productApiVersion) {
		DefaultArtifactVersion deployerApi = new DefaultArtifactVersion(deployerApiVersion);
		DefaultArtifactVersion productApi = new DefaultArtifactVersion(productApiVersion);
		return deployerApi.compareTo(productApi) >= 0;
	}

	@Override
	public File getProductFile(String coords) {
		Artifact art = new DefaultArtifact(coords);
		return getProductFile(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getExtension());
	}

	@Override
	public File getProductWithDependency(String coords) {
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
	@SneakyThrows
	public void loadProductDependency(File repository) {
		List<Artifact> artifacts = componentsToArtifacts();
		artifacts = resolveDependencies(artifacts);
		saveComponents(artifacts, repository);
		FileUtils.deleteDirectory(TMP_REPOSITORY);
	}

	private void saveProduct(List<Artifact> artifacts, File repository, File productFile) {
		artifacts = saveComponents(artifacts, repository);
		instantiateClassLoader(artifacts);
		loadProduct(productFile);
	}

	private File downloadProduct(String groupId, String artifactId, String version, String extension, File productFile) {
		Set<String> products = productList.getProducts().keySet();
		if (!products.contains(groupId + ":" + artifactId)) return null;
		for (ArtifactoryReader repo : productList.getRepos()) {
			try {
				if (!repo.getProductVersions(groupId, artifactId).contains(version)) continue;
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
		List<RemoteRepository> remoteRepos = new ArrayList<>();
		urls.forEach(url -> remoteRepos.add(new RemoteRepository.Builder(url, "default", url).build()));
		DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
		for (Artifact artifact : artifacts) {
			List<Artifact> deps = new ArrayList<>();
			collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
			collectRequest.setRepositories(remoteRepos);
			DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter);
			try {
				List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
				artifactResults.forEach(artifactResult -> {
					Artifact art = artifactResult.getArtifact();
					art = fileSetter(art, workingRepository);
					deps.add(art);
				});
				depCtx.put(artifact.getArtifactId(), getDeploymentContext(artifact, deps));
				components.addAll(deps);
			} catch (DependencyResolutionException e) {
				FileUtils.forceDelete(TMP_REPOSITORY);
				throw new RuntimeException(e);
			}
		}
		return components;
	}

	@SneakyThrows
	private File getProductFile(String groupId, String artifactId, String version, String extension) {
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
			if (res == null)
				throw new EProductNotFound(Utils.coordsToFileName(groupId, artifactId, version) + " is not found in all known repositories");
		}
		product.getProductStructure();
		loader.close();
		FileUtils.deleteDirectory(TMP_REPOSITORY);
		return res;
	}

	@SneakyThrows
	private List<Artifact> saveComponents(List<Artifact> artifacts, File repository) {
		session = Utils.newRepositorySystemSession(system, repository);
		InstallRequest installRequest = new InstallRequest();
		for (Artifact artifact : artifacts) {
			artifact = fileSetter(artifact, TMP_REPOSITORY);
			Artifact pomArtifact = new SubArtifact(artifact, "", "pom");
			pomArtifact = pomArtifact.setFile(new File(TMP_REPOSITORY, Utils.coordsToRelativeFilePath(artifact.getGroupId(),
					artifact.getArtifactId(), artifact.getVersion(), ".pom")));
			installRequest.addArtifact(artifact).addArtifact(pomArtifact);
		}
		system.install(session, installRequest);
		artifacts = artifacts.stream().map(art -> fileSetter(art, repository)).collect(Collectors.toList());
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

	private String readProductApiVersion(File productFile) {
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
		String productApiVersion = apiDep.map(org.apache.maven.model.Dependency::getVersion).orElse("");
		if (log.isDebugEnabled()) {
			log.debug("Product API version is " + productApiVersion);
			log.debug("Deployer API version is " + deployerApiVersion);
		}
		return productApiVersion;
	}

	@SneakyThrows
	private void loadProduct(File productFile) {
		String productApiVersion = readProductApiVersion(productFile);
		if (productApiVersion.endsWith("SNAPSHOT") || productApiVersion.isEmpty() ||
				compareApiVersions(deployerApiVersion, productApiVersion)) {
			String mainClassName = Utils.getExportedClassName(productFile);
			Object obj;
			obj = loader.loadClass(mainClassName).getConstructor().newInstance();
			if (obj instanceof IProduct)
				product = (IProduct) obj;
			else
				throw new RuntimeException("Current product doesn't implement IProduct");
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

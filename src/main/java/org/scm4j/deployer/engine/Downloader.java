package org.scm4j.deployer.engine;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.scm4j.deployer.api.DeploymentContext;
import org.scm4j.deployer.api.IComponent;
import org.scm4j.deployer.api.IDeploymentContext;
import org.scm4j.deployer.api.IDownloader;
import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.api.ProductInfo;
import org.scm4j.deployer.engine.exceptions.EIncompatibleApiVersion;
import org.scm4j.deployer.engine.exceptions.EProductListEntryNotFound;
import org.scm4j.deployer.engine.exceptions.EProductNotFound;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@Slf4j
class Downloader implements IDownloader {

	public static final String REPOSITORY_FOLDER_NAME = "repository";
	private static final String API_NAME = "scm4j-deployer-api";
	private final Map<String, IDeploymentContext> depCtx;
	private final ProductList productList;
	private final File workingRepository;
	private final File portableRepository;
	private final RepositorySystem system;
	private RepositorySystemSession session;
	private URLClassLoader loader;
	private IProduct product;

	Downloader(File portableFolder, File workingFolder, String... productListArtifactoryUrls) {
		this.workingRepository = new File(workingFolder, REPOSITORY_FOLDER_NAME);
		this.portableRepository = new File(portableFolder, REPOSITORY_FOLDER_NAME);
		if (!portableRepository.exists())
			portableRepository.mkdirs();
		if (!workingRepository.exists())
			workingRepository.mkdirs();
		this.productList = new ProductList(portableRepository, productListArtifactoryUrls);
		this.system = Utils.newRepositorySystem();
		this.depCtx = new HashMap<>();
	}

	private static Artifact fileSetter(Artifact art, File repository) {
		art = art.setFile(new File(repository, Utils.coordsToRelativeFilePath(art.getGroupId(),
				art.getArtifactId(), art.getVersion(), art.getExtension(), art.getClassifier())));
		return art;
	}

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
	public void getProductFile(String coords) {
		Artifact art = new DefaultArtifact(coords);
		getProductFile(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getExtension(),
				art.getClassifier());
	}

	@Override
	public void getProductWithDependency(String coords) {
		Artifact art = new DefaultArtifact(coords);
		File productFile = getProductFile(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getExtension(),
				art.getClassifier());
		String fileRelativePath = Utils.coordsToRelativeFilePath(art.getGroupId(), art.getArtifactId(),
				art.getVersion(), art.getExtension(), art.getClassifier());
		if (productFile.equals(new File(portableRepository, fileRelativePath)))
			loadProductDependency(portableRepository);
		else
			loadProductDependency(workingRepository);
		if (!product.getDependentProducts().isEmpty()) {
			for (String dependentCoords : product.getDependentProducts()) {
				getProductWithDependency(dependentCoords);
			}
		}
	}

	@Override
	@SneakyThrows
	public void loadProductDependency(File repository) {
		List<Artifact> artifacts = product.getProductStructure().getComponents().stream()
				.map(IComponent::getArtifactCoords)
				.collect(Collectors.toList());
		resolveDependencies(artifacts, repository);
	}

	private File downloadProduct(String groupId, String artifactId, String version, String extension, String classifier,
	                             File productFile) {
		Collection<String> products = productList.getProducts().values().stream()
				.map(ProductInfo::getArtifactId)
				.collect(Collectors.toList());
		if (!products.contains(groupId + ":" + artifactId))
			return null;
		for (ArtifactoryReader repo : productList.getRepos()) {
			try {
				if (!repo.getProductVersions(groupId + ":" + artifactId).contains(version)) continue;
			} catch (Exception e) {
				continue;
			}
			List<Artifact> artifacts = resolveDependencies(
					Collections.singletonList(new DefaultArtifact(groupId, artifactId, classifier, extension, version)),
					portableRepository);
			instantiateClassLoader(artifacts);
			loadProduct(productFile);
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
	private List<Artifact> resolveDependencies(List<Artifact> artifacts, File repository) {
		List<Artifact> components = new ArrayList<>();
		session = Utils.newRepositorySystemSession(system, repository);
		List<String> urls = productList.getRepos().stream().map(ArtifactoryReader::toString).collect(Collectors.toList());
		urls.add(0, portableRepository.toURI().toURL().toString());
		if (!portableRepository.equals(workingRepository))
			urls.add(0, workingRepository.toURI().toURL().toString());
		List<RemoteRepository> remoteRepos = new ArrayList<>();
		urls.forEach(url -> {
			RemoteRepository rep = new RemoteRepository.Builder(url, "default", url)
					.setPolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY,
							RepositoryPolicy.CHECKSUM_POLICY_IGNORE))
					.build();
			remoteRepos.add(rep);
		});
		for (Artifact artifact : artifacts) {
			List<Artifact> deps;
			try {
				if (artifact.getExtension().equals("jar"))
					deps = resolveJar(remoteRepos, artifact, repository);
				else
					deps = resolveNotJar(remoteRepos, artifact, repository);
				depCtx.put(artifact.getArtifactId() + artifact.getVersion(), getDeploymentContext(artifact, deps));
				components.addAll(deps);
			} catch (DependencyResolutionException | ArtifactResolutionException e) {
				throw new RuntimeException(e);
			}
		}
		return components;
	}

	private List<Artifact> resolveJar(List<RemoteRepository> repos, Artifact art, File repo) throws DependencyResolutionException {
		CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(new Dependency(art, null));
		collectRequest.setRepositories(repos);
		DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
		List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
		List<Artifact> deps = new ArrayList<>();
		artifactResults.forEach(artifactResult -> {
			Artifact artifact = artifactResult.getArtifact();
			artifact = fileSetter(artifact, repo);
			deps.add(artifact);
		});
		return deps;
	}

	private List<Artifact> resolveNotJar(List<RemoteRepository> repos, Artifact art, File repo) throws ArtifactResolutionException {
		ArtifactRequest req = new ArtifactRequest();
		req.setRepositories(repos);
		req.setArtifact(new SubArtifact(art, "", "pom"));
		system.resolveArtifact(session, req);
		req.setArtifact(art);
		ArtifactResult res = system.resolveArtifact(session, req);
		art = res.getArtifact();
		art = fileSetter(art, repo);
		return Collections.singletonList(art);
	}

	@SneakyThrows
	private File getProductFile(String groupId, String artifactId, String version, String extension, String classifier) {
		if (productList.getRepos() == null || productList.getProducts() == null) {
			throw new EProductListEntryNotFound("Product list doesn't loaded");
		}
		String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension, classifier);
		File res = new File(portableRepository, fileRelativePath);
		if (res.exists()) {
			List<Artifact> artifacts = resolveDependencies(
					Collections.singletonList(new DefaultArtifact(groupId, artifactId, classifier, extension, version)),
					workingRepository);
			instantiateClassLoader(artifacts);
			loadProduct(res);
			res = new File(workingRepository, fileRelativePath);
		} else {
			res = downloadProduct(groupId, artifactId, version, extension, classifier, res);
			if (res == null)
				throw new EProductNotFound(Utils.coordsToFileName(artifactId, version, extension)
						+ " is not found in all known repositories");
		}
		product.getProductStructure();
		return res;
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
			log.debug("Deployer API version is " + readDeployerApiVersion());
		}
		return productApiVersion;
	}

	@SneakyThrows
	private String readDeployerApiVersion() {
		String resourceName = Downloader.class.getPackage().getName().replace('.', '/') + "/"
				+ API_NAME + "-version";
		@Cleanup
		InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName);
		if (is == null)
			return null;
		else
			return IOUtils.toString(is, "UTF-8");
	}

	@SneakyThrows
	private void loadProduct(File productFile) {
		String productApiVersion = readProductApiVersion(productFile);
		Optional<String> deployerApiVersion = Optional.ofNullable(readDeployerApiVersion());
		if (productApiVersion.endsWith("SNAPSHOT") || !deployerApiVersion.isPresent() ||
				deployerApiVersion.get().endsWith("SNAPSHOT") ||
				compareApiVersions(deployerApiVersion.get(), productApiVersion)) {
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
			throw new EIncompatibleApiVersion("Can't load " + productFile.getName() + " class to classpath");
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IDeploymentContext> T getContextByArtifactIdAndVersion(String artifactId, String version) {
		return (T) depCtx.get(artifactId + version);
	}
}

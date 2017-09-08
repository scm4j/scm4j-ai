package org.scm4j.ai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

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
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.scm4j.ai.exceptions.EArtifactNotFound;
import org.scm4j.ai.exceptions.ENoConfig;
import org.scm4j.ai.exceptions.EProductNotFound;
import org.scm4j.ai.installers.InstallerFactory;
import org.yaml.snakeyaml.Yaml;

public class AIRunner {

	public static final String REPOSITORY_FOLDER_NAME = "repository";
	private File repository;
	private InstallerFactory installerFactory;
	private RepositorySystem system;
	private RepositorySystemSession session;
	private ProductList productList;

	public void setInstallerFactory(InstallerFactory installerFactory) {
		this.installerFactory = installerFactory;
	}

	//TODO Download ProductList
	public AIRunner(File workingFolder, String productListArtifactoryUrl, String userName, String password) throws ENoConfig {
		repository = new File(workingFolder, REPOSITORY_FOLDER_NAME);
		productList = new ProductList(repository);
		try {
			if (!repository.exists()) {
				Files.createDirectory(repository.toPath());
			}
			productList.downloadProductList(productListArtifactoryUrl, userName, password);
		} catch (Exception e) {
			throw new RuntimeException (e);
		}

		system = Utils.newRepositorySystem();
		session = Utils.newRepositorySystemSession(system,repository);
	}

	public List<String> getProductVersions(String groupId, String artifactId) throws Exception{
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

	public File download(String groupId, String artifactId, String version, String extension){
		String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
		File res = new File(repository, fileRelativePath);
		File pom = new File(res.getParent(), Utils.coordsToFileName(artifactId, version, ArtifactoryReader.POM_FILE_EXTENTION));
		for (ArtifactoryReader repo : productList.getRepos()) {
			try {
				if (!productList.getProducts().contains(Utils.coordsToString(groupId, artifactId))) {
					continue;
				}
			} catch (Exception e) {
				continue;
			}

			try {
				if (!getProductVersions(groupId, artifactId).contains(version)) {
					continue;
				}
			} catch (Exception e) {
				continue;
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
			try (FileOutputStream out = new FileOutputStream(res);
				 InputStream in = repo.getContentStream(groupId, artifactId, version, extension)) {
				IOUtils.copy(in, out);
			} catch (Exception e1) {
				continue;
			}
			try(FileOutputStream outPom = new FileOutputStream(pom);
				InputStream inPom = repo.getContentStream(groupId, artifactId, version, ArtifactoryReader.POM_FILE_EXTENTION)) {
				IOUtils.copy(inPom,outPom);
			} catch (Exception e2) {
				continue;
			}
			try {
				List<Artifact> artifacts = getComponents(groupId, artifactId, version);
				downloadComponents(artifacts);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return res;
		}
		return null;
	}

	//TODO install artifacts in local-repo
	public void downloadComponents(List<Artifact> artifacts) throws DependencyResolutionException {
		CollectRequest collectRequest = new CollectRequest();
		for(ArtifactoryReader reader : productList.getRepos()) {
			collectRequest.addRepository(new RemoteRepository.Builder("","default", reader.toString()).build());
		}
		for(Artifact artifact : artifacts) {
			DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
			collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
			DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,filter);
			system.resolveDependencies(session,dependencyRequest);
		}
	}

	public List<Artifact> getComponents(String groupId, String artifactId, String version) throws Exception {
		List<Artifact> artifacts = new ArrayList<>();
		Map<String, String> product;
		URL productUrl = productList.getProductListReader().getProductUrl(groupId, artifactId, version, ".yml");
		try (InputStream is = productList.getProductListReader().getContentStream(productUrl)) {
			Yaml yaml = new Yaml();
			product = yaml.loadAs(is, HashMap.class);
		}
		Set<String> components = product.keySet();
		for(String component : components) {
			Artifact artifact = new DefaultArtifact(component);
			artifacts.add(artifact);
		}
		return artifacts;
	}

	public ProductList getProductList() {
		return productList;
	}
}

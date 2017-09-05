package org.scm4j.ai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

import org.apache.commons.io.IOUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
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
	private List<ArtifactoryReader> repos = new ArrayList<>();
	private File repository;
	private InstallerFactory installerFactory;

	public void setInstallerFactory(InstallerFactory installerFactory) {
		this.installerFactory = installerFactory;
	}

	//TODO put URL with product-list(ver).yml to AIRunner
	public AIRunner(File workingFolder) throws ENoConfig {
		repository = new File(workingFolder, REPOSITORY_FOLDER_NAME);
		try {
			if (!repository.exists()) {
				Files.createDirectory(repository.toPath());
			}
		} catch (Exception e) {
			throw new RuntimeException (e);
		}

		setRepos(ArtifactoryReader.loadFromWorkingFolder(workingFolder));
	}

	public List<ArtifactoryReader> getRepos() {
		return repos;
	}

	public List<String> listProducts() {
		Set<String> res = new HashSet<>();
		try {
			for (ArtifactoryReader repo : getRepos()) {
				res.addAll(repo.getProducts().get(ArtifactoryReader.PRODUCTS));
			}
			return new ArrayList<>(res);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public List<String> listVersions(String groupId, String artifactId) {
		try {
			for (ArtifactoryReader repo : getRepos()) {
				if (repo.hasProduct(groupId, artifactId)) {
					return repo.getProductVersions(groupId, artifactId);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		throw new EProductNotFound();
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

	private File download(String groupId, String artifactId, String version, String extension){
		String fileRelativePath = Utils.coordsToRelativeFilePath(groupId, artifactId, version, extension);
		File res = new File(repository, fileRelativePath);
		File pom = new File(res + ArtifactoryReader.POM_FILE_EXTENTION);
		for (ArtifactoryReader repo : getRepos()) {
			try {
				if (!repo.getProducts().get(ArtifactoryReader.PRODUCTS).contains(Utils.coordsToString(groupId, artifactId))) {
					continue;
				}
			} catch (Exception e) {
				continue;
			}

			try {
				if (!repo.getProductVersions(groupId, artifactId).contains(version)) {
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

			//TODO Разбить исключения на исключения связанные с чтением и на исключения записи
			try (FileOutputStream out = new FileOutputStream(res);
				 InputStream in = repo.getContentStream(groupId, artifactId, version, extension)) {
				IOUtils.copy(in, out);
			} catch (Exception e1) {
				continue;
			}
			try(FileOutputStream outPom = new FileOutputStream(pom);
				InputStream inPom = repo.getContentStream(repo.getProductPomURL(groupId, artifactId, version))) {
				IOUtils.copy(inPom,outPom);
			} catch (Exception e2) {
				continue;
			}
			try {
				List<Artifact> artifacts = repo.getComponents(groupId, artifactId, version);
				downloadComponents(artifacts);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return res;
		}
		return null;
	}

	//TODO Convert <ArtifactoryReader> to <RemoteRepository> for downloading deps
	public void downloadComponents(List<Artifact> artifacts) throws DependencyResolutionException {
		RepositorySystem system = Utils.newRepositorySystem();
		RepositorySystemSession session = Utils.newRepositorySystemSession(system, repository);
		for(Artifact artifact : artifacts) {
			DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
			CollectRequest collectRequest = new CollectRequest();
			collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
			collectRequest.addRepository(new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build());
			DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,filter);
			system.resolveDependencies(session,dependencyRequest);
		}
	}

	public void setRepos(List<ArtifactoryReader> repos) {
		this.repos = repos;
	}
}

package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.SneakyThrows;
import org.eclipse.aether.artifact.Artifact;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IProductDeployer;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Data
public class DeployerEngine implements IProductDeployer {

	private final Downloader downloader;
	private final Deployer deployer;

	public DeployerEngine(File portableFolder, File workingFolder, String productListArtifactoryUrl) {
		if (portableFolder == null)
			portableFolder = workingFolder;
		this.downloader = new Downloader(portableFolder, workingFolder, productListArtifactoryUrl);
		this.deployer = new Deployer(workingFolder, downloader);
	}

	@Override
	public DeploymentResult deploy(String simpleName, String version) {
		listProducts();
		Artifact artifact = Utils.initializeArtifact(downloader, simpleName, version);
		DeploymentResult res = deployer.deploy(artifact, simpleName);
		URLClassLoader loader = downloader.getLoader();
		if (loader != null) {
			try {
				loader.close();
			} catch (IOException e) {
				//No problem
			}
		}
		return res;
	}

	@Override
	public void download(String simpleName, String version) {
		listProducts();
		Artifact artifact = Utils.initializeArtifact(downloader, simpleName, version);
		downloader.getProductWithDependency(artifact.toString());
		URLClassLoader loader = downloader.getLoader();
		if (loader != null) {
			try {
				loader.close();
			} catch (IOException e) {
				//No problem
			}
		}
	}

	@Override
	@SneakyThrows
	@SuppressWarnings("unchecked")
	public List<String> listProducts() {
		Map<String, Map<String, String>> entry = downloader.getProductList().readFromProductList();
		return new ArrayList<>(entry.get(ProductList.PRODUCTS).keySet());
	}

	@Override
	@SneakyThrows
	public List<String> refreshProducts() {
		downloader.getProductList().downloadProductList();
		return listProducts();
	}

	@Override
	public Map<String, Boolean> listProductVersions(String simpleName) {
		Optional<Set<String>> versions = Optional.ofNullable
				(downloader.getProductList().readProductVersions(simpleName).get(simpleName));
		Map<String, Boolean> downloadedVersions = new LinkedHashMap<>();
		versions.ifPresent(strings -> strings.forEach
				(version -> downloadedVersions.put(version, versionDownloaded(simpleName, version))));
		return downloadedVersions;
	}

	private boolean versionDownloaded(String simpleName, String version) {
		String groupIdAndArtId = downloader.getProductList().getProducts().getOrDefault(simpleName, "");
		String[] arr = groupIdAndArtId.split(":");
		File productVersionFolder = new File(downloader.getPortableRepository(), Utils.coordsToFolderStructure(arr[0],
				arr[1], version));
		if (!productVersionFolder.exists())
			productVersionFolder = new File(downloader.getWorkingRepository(), Utils.coordsToFolderStructure(arr[0],
					arr[1], version));
		return productVersionFolder.exists();
	}

	@Override
	public Map<String, Boolean> refreshProductVersions(String simpleName) {
		try {
			downloader.getProductList().refreshProductVersions(simpleName);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return listProductVersions(simpleName);
	}

	@Override
	public Map<String, Boolean> listDeployedProducts(String simpleName) {
		HashMap<String, Boolean> deployed = new HashMap<>();
		Map<String, ProductDescription> deployedProducts = deployer.listDeployedProducts();
		String groupIdAndArtId = downloader.getProductList().getProducts().getOrDefault(simpleName, "");
		ProductDescription desc = deployedProducts.getOrDefault(groupIdAndArtId,
				null);
		if (desc == null) {
			return deployed;
		} else {
			String version = desc.getProductVersion();
			deployed.put(version, true);
			return deployed;
		}
	}
}
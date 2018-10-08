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
import java.util.Collections;
import java.util.HashMap;
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
	public List<String> listProductVersions(String simpleName) {
		Optional<Set<String>> versions = Optional.ofNullable
				(downloader.getProductList().readProductVersions(simpleName).get(simpleName));
		return new ArrayList<>(versions.orElse(Collections.emptySet()));
	}

	@Override
	public List<String> refreshProductVersions(String simpleName) {
		try {
			downloader.getProductList().refreshProductVersions(simpleName);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return listProductVersions(simpleName);
	}

	@Override
	public Map<String, String> mapDeployedProducts(String simpleName) {
		Map<String, String> deployed = new HashMap<>();
		Map<String, ProductDescription> deployedProducts = deployer.listDeployedProducts();
		for (Map.Entry<String, ProductDescription> product : deployedProducts.entrySet()) {
			ProductDescription desc = product.getValue();
			deployed.put(desc.getProductName(), desc.getProductVersion());
		}
		return deployed;
	}
}
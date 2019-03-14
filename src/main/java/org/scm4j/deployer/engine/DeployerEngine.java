package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.SneakyThrows;
import org.eclipse.aether.artifact.Artifact;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IProductDeployer;
import org.scm4j.deployer.api.ProductInfo;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
public class DeployerEngine implements IProductDeployer {

	private final Downloader downloader;
	private final Deployer deployer;

	public DeployerEngine(File portableFolder, File workingFolder, String... productListArtifactoryUrls) {
		if (portableFolder == null)
			portableFolder = workingFolder;
		this.downloader = new Downloader(portableFolder, workingFolder, productListArtifactoryUrls);
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
	public Map<String, ProductInfo> listProducts() {
		ProductListEntry entry = downloader.getProductList().readFromProductList();
		return entry.getProducts();
	}

	@Override
	@SneakyThrows
	public Map<String, ProductInfo> refreshProducts() {
		downloader.getProductList().downloadProductList();
		return listProducts();
	}

	@Override
	public Map<String, Boolean> listProductVersions(String simpleName) {
		Optional<Map<String, Boolean>> versions = Optional.ofNullable
				(downloader.getProductList().readProductVersions(simpleName));
		return versions.orElse(Collections.emptyMap());
	}

	@Override
	public Map<String, Boolean> refreshProductVersions(String simpleName) {
		try {
			downloader.getProductList().downloadProductsVersions();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return listProductVersions(simpleName);
	}

	@Override
	public Map<String, String> mapDeployedProducts() {
		Map<String, String> deployed = new HashMap<>();
		Map<String, ProductDescription> deployedProducts = deployer.listDeployedProducts();
		for (Map.Entry<String, ProductDescription> product : deployedProducts.entrySet()) {
			ProductDescription desc = product.getValue();
			deployed.put(desc.getProductName(), desc.getProductVersion());
		}
		return deployed;
	}
}
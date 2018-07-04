package org.scm4j.deployer.engine;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.scm4j.deployer.api.DeployedProduct;
import org.scm4j.deployer.api.DeploymentContext;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IComponent;
import org.scm4j.deployer.api.IComponentDeployer;
import org.scm4j.deployer.api.IDeployedProduct;
import org.scm4j.deployer.api.IDownloader;
import org.scm4j.deployer.api.IImmutable;
import org.scm4j.deployer.api.ILegacyProduct;
import org.scm4j.deployer.api.IProduct;
import org.scm4j.deployer.api.IProductStructure;
import org.scm4j.deployer.api.ProductStructure;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.scm4j.deployer.api.DeploymentResult.ALREADY_INSTALLED;
import static org.scm4j.deployer.api.DeploymentResult.FAILED;
import static org.scm4j.deployer.api.DeploymentResult.INCOMPATIBLE_API_VERSION;
import static org.scm4j.deployer.api.DeploymentResult.NEED_REBOOT;
import static org.scm4j.deployer.api.DeploymentResult.NEWER_VERSION_EXISTS;
import static org.scm4j.deployer.api.DeploymentResult.OK;
import static org.scm4j.deployer.api.DeploymentResult.REBOOT_CONTINUE;
import static org.scm4j.deployer.engine.Deployer.Command.DEPLOY;
import static org.scm4j.deployer.engine.Deployer.Command.START;
import static org.scm4j.deployer.engine.Deployer.Command.STOP;
import static org.scm4j.deployer.engine.Deployer.Command.UNDEPLOY;

@Slf4j
@Data
class Deployer {

	private static final String DEPLOYED_PRODUCTS = "deployed-products.yml";
	private final IDownloader downloader;
	private final File workingFolder;
	private final File deployedProductsFile;
	private String deploymentPath;

	Deployer(File workingFolder, IDownloader downloader) {
		this.workingFolder = workingFolder;
		this.downloader = downloader;
		deployedProductsFile = new File(workingFolder, DEPLOYED_PRODUCTS);
	}

	private static DeploymentResult compareVersionWithDeployedVersion(String version, String legacyVersion,
	                                                                  String productName, String coords) {
		DeploymentResult res = OK;
		if (!version.isEmpty()) {
			DefaultArtifactVersion vers = new DefaultArtifactVersion(version);
			DefaultArtifactVersion legacyVers = new DefaultArtifactVersion(legacyVersion);
			if (vers.compareTo(legacyVers) == 0) {
				log.info(productName + " already installed!");
				res = ALREADY_INSTALLED;
			}
			if (vers.compareTo(legacyVers) < 0) {
				log.info(productName + " newer version exist");
				res = NEWER_VERSION_EXISTS;
			}
		}
		res.setProductCoords(coords);
		return res;
	}

	@SneakyThrows
	private static void writeLatestFileForImmutableProduct(IProduct product, String version) {
		File latest = new File(product.getProductStructure().getDefaultDeploymentPath(), version);
		latest = new File(latest.getParent(), "latest");
		if (latest.exists()) {
			changeLatest(latest, version);
		} else {
			FileUtils.writeStringToFile(latest, version, "UTF-8");
		}
	}

	@SneakyThrows
	private static void changeLatest(File latest, String version) {
		String deployedVersion = FileUtils.readFileToString(latest, "UTF-8");
		DefaultArtifactVersion depVersion = new DefaultArtifactVersion(deployedVersion);
		DefaultArtifactVersion requiredVersion = new DefaultArtifactVersion(version);
		if (depVersion.compareTo(requiredVersion) < 0) {
			FileUtils.writeStringToFile(latest, version, "UTF-8");
		}
	}

	static Map<Command, List<IComponent>> compareProductStructures(IProductStructure requiredPS, IProductStructure deployedPS) {
		Map<Command, List<IComponent>> comparedComponents = new HashMap<>();
		List<IComponent> requiredArts = requiredPS.getComponents();
		List<IComponent> deployedArts = deployedPS.getComponents();
		List<IComponent> artsForUndeploy = new ArrayList<>(deployedArts);
		artsForUndeploy.removeAll(requiredArts);
		requiredArts.removeAll(deployedArts);
		comparedComponents.put(DEPLOY, requiredArts);
		comparedComponents.put(UNDEPLOY, artsForUndeploy);
		return comparedComponents;
	}

	@SuppressWarnings("unchecked")
	private void writeProductDescriptionInDeployedProductsYaml(String coords, String version) {
		ProductDescription productDescription = createProductDescription(version);
		Map<String, ProductDescription> deployedProducts = Utils.readYml(deployedProductsFile);
		deployedProducts.put(coords, productDescription);
		Utils.writeYaml(deployedProducts, deployedProductsFile);
	}

	private ProductDescription createProductDescription(String version) {
		ProductDescription newProduct = new ProductDescription();
		newProduct.setProductVersion(version);
		newProduct.setDeploymentPath(deploymentPath);
		newProduct.setDeploymentTime(System.currentTimeMillis());
		return newProduct;
	}

	@SuppressWarnings("unchecked")
	DeploymentResult deploy(Artifact art) {
		DeploymentResult res;
		String coords = String.format("%s:%s", art.getGroupId(), art.getArtifactId());
		String artifactId = art.getArtifactId();
		String version = art.getVersion();
		String productName = artifactId + "-" + version;
		Map<String, ProductDescription> deployedProducts = Utils.readYml(deployedProductsFile);
		DeployedProduct deployedProduct;
		IProduct requiredProduct;
		ProductDescription productDescription;
		if (version.isEmpty()) {
			requiredProduct = ProductStructure::createEmptyStructure;
		} else {
			downloader.getProductFile(art.toString());
			requiredProduct = downloader.getProduct();
		}
		productDescription = deployedProducts.get(coords);
		if (productDescription != null && !productDescription.getProductVersion().equals("")) {
			String deployedVersion = productDescription.getProductVersion();
			res = compareVersionWithDeployedVersion(version, deployedVersion, productName, coords);
			if (res == ALREADY_INSTALLED || res == NEWER_VERSION_EXISTS) {
				return res;
			}
			deployedProduct = createDeployedProduct(coords + ":" + art.getExtension(), deployedVersion,
					productDescription);
		} else if (version.isEmpty()) {
			log.info(productName + " isn't installed!");
			res = OK;
			res.setProductCoords(coords);
			return res;
		} else if (requiredProduct instanceof ILegacyProduct) {
			deployedProduct = ((ILegacyProduct) requiredProduct).queryLegacyDeployedProduct();
			if (deployedProduct != null) {
				res = compareVersionWithDeployedVersion(version, deployedProduct.getProductVersion(), productName, coords);
				if (res == ALREADY_INSTALLED || res == NEWER_VERSION_EXISTS) {
					deploymentPath = deployedProduct.getDeploymentPath();
					writeProductDescriptionInDeployedProductsYaml(coords, deployedProduct.getProductVersion());
				}
				if (res != OK)
					return res;
			}
		} else {
			deployedProduct = null;
		}
		downloader.loadProductDependency(new File(workingFolder, "repository"));
		res = compareAndDeployProducts(requiredProduct, deployedProduct, artifactId, version, coords);
		res.setProductCoords(coords);
		if (res == OK || res == NEED_REBOOT) {
			writeProductDescriptionInDeployedProductsYaml(coords, version);
			if (requiredProduct instanceof IImmutable)
				writeLatestFileForImmutableProduct(requiredProduct, version);
			return res;
		} else {
			return res;
		}
	}

	private DeployedProduct createDeployedProduct(String coords, String deployedVersion,
	                                              ProductDescription productDescription) {
		DeployedProduct deployedProduct = new DeployedProduct();
		deployedProduct.setProductVersion(deployedVersion);
		deployedProduct.setDeploymentPath(productDescription.getDeploymentPath());
		downloader.getProductFile(coords + ":" + productDescription.getProductVersion());
		IProductStructure ps = downloader.getProduct().getProductStructure();
		deployedProduct.setProductStructure(ps);
		return deployedProduct;
	}

	DeploymentResult compareAndDeployProducts(IProduct requiredProduct, IDeployedProduct deployedProduct,
	                                          String artifactId, String version, String coords) {
		DeploymentResult res;
		String productName = artifactId + "-" + version;
		if (!requiredProduct.getDependentProducts().isEmpty()) {
			res = deployDependent(requiredProduct);
			if (res == FAILED || res == NEED_REBOOT || res == INCOMPATIBLE_API_VERSION)
				return res;
		}
		Map<Command, List<IComponent>> changedComponents;
		if (deployedProduct != null) {
			List<IComponent> deployedComponents = deployedProduct.getProductStructure().getComponents();
			changedComponents = compareProductStructures(requiredProduct.getProductStructure(),
					deployedProduct.getProductStructure());
			deploymentPath = deployedProduct.getDeploymentPath();
			deployedComponents = Lists.reverse(deployedComponents);
			res = doCommands(deployedComponents, STOP);
			if (res != OK)
				return res;
			res = doCommands(changedComponents.getOrDefault(UNDEPLOY, Collections.emptyList()), UNDEPLOY);
			if (res != OK)
				return res;
			else
				writeProductDescriptionInDeployedProductsYaml(coords, "");
		} else {
			changedComponents = compareProductStructures(requiredProduct.getProductStructure(), ProductStructure.createEmptyStructure());
			deploymentPath = requiredProduct.getProductStructure().getDefaultDeploymentPath();
		}
		if (requiredProduct instanceof IImmutable)
			deploymentPath = requiredProduct.getProductStructure().getDefaultDeploymentPath() + "/" + version;
		List<IComponent> componentForDeploy;
		if (!changedComponents.get(DEPLOY).isEmpty()) {
			componentForDeploy = changedComponents.get(DEPLOY);
			res = doCommands(componentForDeploy, DEPLOY);
			if (res != OK)
				return res;
			res = doCommands(requiredProduct.getProductStructure().getComponents(), START);
			if (res != OK)
				return res;
			log.info(productName + " successfully deployed");
			return res;
		} else {
			log.info(productName + " successfully undeployed");
			res = OK;
			return res;
		}
	}

	private DeploymentResult doCommands(List<IComponent> components, Command cmd) {
		DeploymentResult res = OK;
		for (IComponent component : components) {
			res = applyCommand(component, cmd);
			if (res != OK)
				return res;
		}
		return res;
	}

	private DeploymentResult deployDependent(IProduct product) {
		List<Artifact> dependents = product.getDependentProducts().stream()
				.map(DefaultArtifact::new)
				.collect(Collectors.toList());
		DeploymentResult res = OK;
		for (Artifact dependent : dependents) {
			res = deploy(dependent);
			if (res == FAILED || res == NEED_REBOOT || res == REBOOT_CONTINUE)
				return res;
		}
		return res;
	}

	private DeploymentResult applyCommand(IComponent component, Command command) {
		List<IComponentDeployer> deployers = component.getDeploymentProcedure().getComponentDeployers();
		DeploymentResult res;
		Artifact coords = component.getArtifactCoords();
		String artifactId = coords.getArtifactId();
		for (int i = 0; i < deployers.size(); i++) {
			IComponentDeployer deployer = deployers.get(i);
			File currentDeployerLog = new File(System.getProperty("java.io.tmpdir"), artifactId + "-"
					+ coords.getVersion() + "-" + deployer.getClass().getSimpleName() + "-" + i + ".txt");
			int rebootCount = 0;
			if (currentDeployerLog.exists()) {
				rebootCount = readRebootCount(currentDeployerLog);
			}
			DeploymentContext context;
			if (artifactId.equals("legacyComponent"))
				context = new DeploymentContext("legacyProduct");
			else
				context = downloader.getContextByArtifactId(artifactId);
			context.setDeploymentPath(deploymentPath);
			deployer.init(context);
			switch (command) {
			case DEPLOY:
				res = deployer.deploy();
				break;
			case UNDEPLOY:
				res = deployer.undeploy();
				break;
			case STOP:
				res = deployer.stop();
				break;
			case START:
				res = deployer.start();
				break;
			default:
				throw new IllegalArgumentException();
			}
			if (res == FAILED || res == NEED_REBOOT) {
				return res;
			}
			if (res == REBOOT_CONTINUE) {
				if (rebootCount == 2) {
					log.error("Component deployer ask for reboot but it's third reboot for this deployer,"
							+ " deployment failed");
					return FAILED;
				} else {
					writeRebootCount(currentDeployerLog, ++rebootCount);
					return res;
				}
			}
			log.trace(deployer.getClass().getSimpleName() + " done");
		}
		return OK;
	}

	private void writeRebootCount(File fileWithRebootCounts, int rebootCount) {
		try {
			FileUtils.writeStringToFile(fileWithRebootCounts, Integer.toString(rebootCount), "UTF-8");
		} catch (IOException e) {
			log.warn("Can't create file to write reboots count cause of " + e.toString());
		}
	}

	private int readRebootCount(File fileWithRebootCounts) {
		try {
			String countString = FileUtils.readFileToString(fileWithRebootCounts, "UTF-8");
			return Integer.valueOf(countString);
		} catch (IOException e) {
			log.warn("Can't read from reboot count file cause of " + e.toString());
			return 0;
		} finally {
			FileUtils.deleteQuietly(fileWithRebootCounts);
		}
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> listDeployedProducts() {
		return (Map<String, Object>) Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
	}

	enum Command {DEPLOY, UNDEPLOY, STOP, START}
}
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

	private static DeploymentResult compareVersionWithDeployedVersion(String version, String legacyVersion) {
		DeploymentResult res = OK;
		if (!version.isEmpty()) {
			DefaultArtifactVersion vers = new DefaultArtifactVersion(version);
			DefaultArtifactVersion legacyVers = new DefaultArtifactVersion(legacyVersion);
			if (vers.compareTo(legacyVers) == 0) {
				res = ALREADY_INSTALLED;
			}
			if (vers.compareTo(legacyVers) < 0) {
				res = NEWER_VERSION_EXISTS;
			}
		}
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
		String coords = String.format("%s:%s", art.getGroupId(), art.getArtifactId());
		DeploymentResult res = OK;
		String artifactId = art.getArtifactId();
		String version = art.getVersion();
		String productName = artifactId + "-" + version;
		log.info("product to deploy " + productName);
		Map<String, ProductDescription> deployedProducts = Utils.readYml(deployedProductsFile);
		IDeployedProduct deployedProduct;
		IProduct requiredProduct;
		ProductDescription productDescription = deployedProducts.get(coords);
		String deployedVersion = null;
		if (version.isEmpty()) {
			if (productDescription == null) {
				log.info(productName + " isn't installed!");
				res.setProductCoords(coords);
				return res;
			} else {
				requiredProduct = ProductStructure::createEmptyStructure;
			}
		} else {
			downloader.getProductFile(art.toString());
			requiredProduct = downloader.getProduct();
		}
		if (productDescription != null) {
			deployedVersion = productDescription.getProductVersion();
			if (deployedVersion != null && !deployedVersion.isEmpty()) {
				log.info("product description of deployed product is " + productDescription.toString());
				deployedVersion = productDescription.getProductVersion();
				res = compareVersionWithDeployedVersion(version, deployedVersion);
				if (res == ALREADY_INSTALLED || res == NEWER_VERSION_EXISTS) {
					res.setProductCoords(coords);
					return res;
				}
			}
		}
		if (requiredProduct instanceof ILegacyProduct && productDescription == null) {
			log.info("required product is legacy product, trying to compare");
			deployedProduct = ((ILegacyProduct) requiredProduct).queryLegacyDeployedProduct();
			if (deployedProduct != null) {
				deployedVersion = deployedProduct.getProductVersion();
				res = handleLegacyDeployedProduct(version, deployedVersion, deployedProduct);
				if (res != OK) {
					writeProductDescriptionInDeployedProductsYaml(coords, deployedVersion);
					log.info("legacy product " + res.toString());
					res.setProductCoords(coords);
					return res;
				}
			}
		}
		downloader.loadProductDependency(new File(workingFolder, "repository"));
		deployedProduct = createDeployedProduct(coords, deployedVersion, productDescription);
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

	private DeploymentResult handleLegacyDeployedProduct(String currentVersion, String deployedVersion,
	                                                     IDeployedProduct deployedProduct) {
		log.info("legacy version is " + deployedVersion + " and deployment path is "
				+ deployedProduct.getDeploymentPath());
		DeploymentResult res = compareVersionWithDeployedVersion(currentVersion, deployedVersion);
		if (res == ALREADY_INSTALLED || res == NEWER_VERSION_EXISTS) {
			log.info("legacy product already " + res.toString());
			deploymentPath = deployedProduct.getDeploymentPath();
		}
		return res;
	}

	private IDeployedProduct createDeployedProduct(String coords, String deployedVersion,
	                                               ProductDescription productDescription) {
		if (deployedVersion == null || deployedVersion.isEmpty() || productDescription == null) {
			return null;
		} else {
			DeployedProduct deployedProduct = new DeployedProduct();
			deployedProduct.setProductVersion(deployedVersion);
			deployedProduct.setDeploymentPath(productDescription.getDeploymentPath());
			downloader.getProductFile(coords + ":" + productDescription.getProductVersion());
			IProductStructure ps = downloader.getProduct().getProductStructure();
			downloader.loadProductDependency(new File(workingFolder, "repository"));
			deployedProduct.setProductStructure(ps);
			return deployedProduct;
		}
	}

	DeploymentResult compareAndDeployProducts(IProduct requiredProduct, IDeployedProduct deployedProduct,
	                                          String artifactId, String version, String coords) {
		DeploymentResult res;
		String productName = artifactId + "-" + version;
		if (!requiredProduct.getDependentProducts().isEmpty()) {
			log.info("dependent product are " + requiredProduct.getDependentProducts());
			res = deployDependent(requiredProduct);
			if (res == FAILED || res == NEED_REBOOT || res == INCOMPATIBLE_API_VERSION || res == REBOOT_CONTINUE) {
				log.info("deploy dependent result is " + res.toString());
				return res;
			}
		}
		Map<Command, List<IComponent>> changedComponents;
		if (deployedProduct != null && !(requiredProduct instanceof IImmutable)) {
			List<IComponent> deployedComponents = deployedProduct.getProductStructure().getComponents();
			changedComponents = compareProductStructures(requiredProduct.getProductStructure(),
					deployedProduct.getProductStructure());
			log.info("changed components are " + changedComponents);
			deploymentPath = deployedProduct.getDeploymentPath();
			deployedComponents = Lists.reverse(deployedComponents);
			res = doCommands(deployedComponents, STOP);
			if (res != OK) {
				log.info("stop deployed product result is " + res);
				return res;
			}
			log.info("changed components successfully stopped");
			res = doCommands(changedComponents.getOrDefault(UNDEPLOY, Collections.emptyList()), UNDEPLOY);
			if (res != OK) {
				log.info("undeploy deployed product result is " + res);
				return res;
			} else {
				log.info("changed components successfully undeployed");
				writeProductDescriptionInDeployedProductsYaml(coords, "");
			}
		} else {
			changedComponents = compareProductStructures(requiredProduct.getProductStructure(), ProductStructure.createEmptyStructure());
			deploymentPath = requiredProduct.getProductStructure().getDefaultDeploymentPath();
		}
		if (requiredProduct instanceof IImmutable)
			deploymentPath = requiredProduct.getProductStructure().getDefaultDeploymentPath() + "/" + version;
		List<IComponent> componentForDeploy;
		if (!changedComponents.get(DEPLOY).isEmpty()) {
			log.info("components for deploy is " + changedComponents.toString());
			componentForDeploy = changedComponents.get(DEPLOY);
			res = doCommands(componentForDeploy, DEPLOY);
			if (res != OK) {
				log.info("changed components deploy result is " + res.toString());
				return res;
			}
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
				context = downloader.getContextByArtifactIdAndVersion(artifactId, coords.getVersion());
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
			log.info("result of " + deployer + ' ' + command + " is " + res);
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
			log.info("reboot count is " + rebootCount + " and written to file " + fileWithRebootCounts.getPath());
		} catch (IOException e) {
			log.warn("Can't create file to write reboots count cause of " + e.toString());
		}
	}

	private int readRebootCount(File fileWithRebootCounts) {
		try {
			String countString = FileUtils.readFileToString(fileWithRebootCounts, "UTF-8");
			log.info("reboot count is " + countString);
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
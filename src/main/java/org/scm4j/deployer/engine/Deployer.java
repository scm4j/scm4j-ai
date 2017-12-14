package org.scm4j.deployer.engine;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.exceptions.EIncompatibleApiVersion;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.scm4j.deployer.api.DeploymentResult.*;
import static org.scm4j.deployer.engine.Deployer.Command.*;

@Slf4j
@Data
class Deployer {

    private final IDownloader downloader;

    private static final String DEPLOYED_PRODUCTS = "deployed-products.yml";

    private final File workingFolder;
    private final File deployedProductsFile;
    private String deploymentPath;

    Deployer(File workingFolder, IDownloader downloader) {
        this.workingFolder = workingFolder;
        this.downloader = downloader;
        deployedProductsFile = new File(workingFolder, DEPLOYED_PRODUCTS);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    DeploymentResult deploy(Artifact art) throws EIncompatibleApiVersion {
        DeploymentResult res;
        String coords = String.format("%s:%s:%s", art.getGroupId(), art.getArtifactId(), art.getExtension());
        String artifactId = art.getArtifactId();
        String version = art.getVersion();
        String productName = artifactId + "-" + version;
        Map<String, ProductDescription> deployedProducts = Utils.readYml(deployedProductsFile);
        DeployedProduct deployedProduct;
        IProduct requiredProduct;
        ProductDescription productDescription;
        if (version.equals("")) {
            requiredProduct = ProductStructure::createEmptyStructure;
        } else {
            downloader.getProductFile(art.toString());
            requiredProduct = downloader.getProduct();
        }
        if (deployedProducts.get(coords) != null) {
            productDescription = deployedProducts.get(coords);
            String deployedVersion = productDescription.getProductVersion();
            if (deployedVersion.equals(version)) {
                log.info(productName + " already installed!");
                res = ALREADY_INSTALLED;
                res.setProductCoords(coords);
                return res;
            }
            deployedProduct = createDeployedProduct(coords, deployedVersion, productDescription);
        } else if (version.equals("")) {
            log.info(productName + " isn't installed!");
            res = OK;
            res.setProductCoords(coords);
            return res;
        } else if (requiredProduct instanceof ILegacyProduct) {
            deployedProduct = ((ILegacyProduct) requiredProduct).queryLegacyDeployedProduct();
            if (deployedProduct != null) {
                res = compareVersionWithLegacyVersion(version, deployedProduct.getProductVersion(), productName, coords);
                if (res == ALREADY_INSTALLED || res == NEWER_VERSION_EXISTS) {
                    deploymentPath = deployedProduct.getDeploymentPath();
                    writeProductDescriptionInDeployedProductsYaml(coords, deployedProduct.getProductVersion());
                }
                if (res != OK) return res;
            }
        } else {
            deployedProduct = null;
        }
        downloader.loadProductDependency(new File(workingFolder, "repository"));
        res = compareAndDeployProducts(requiredProduct, deployedProduct, artifactId, version);
        res.setProductCoords(coords);
        if (res != OK) return res;
        writeProductDescriptionInDeployedProductsYaml(coords, version);
        return res;
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

    private DeploymentResult compareVersionWithLegacyVersion(String version, String legacyVersion, String productName, String coords) {
        DeploymentResult res = OK;
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
        res.setProductCoords(coords);
        return res;
    }

    @SneakyThrows
    private DeployedProduct createDeployedProduct(String coords, String deployedVersion, ProductDescription productDescription) throws EIncompatibleApiVersion {
        DeployedProduct deployedProduct = new DeployedProduct();
        deployedProduct.setProductVersion(deployedVersion);
        deployedProduct.setDeploymentPath(productDescription.getDeploymentPath());
        downloader.getProductFile(coords + ":" + productDescription.getProductVersion());
        IProductStructure ps = downloader.getProduct().getProductStructure();
        deployedProduct.setProductStructure(ps);
        return deployedProduct;
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    DeploymentResult compareAndDeployProducts(IProduct requiredProduct, IDeployedProduct deployedProduct, String artifactId, String version) throws EIncompatibleApiVersion {
        DeploymentResult res;
        String productName = artifactId + "-" + version;
        if (!requiredProduct.getDependentProducts().isEmpty()) {
            res = deployDependent(requiredProduct);
            if (res == FAILED || res == NEED_REBOOT || res == INCOMPATIBLE_API_VERSION) return res;
        }
        Map<Command, List<IComponent>> changedComponents;
        if (deployedProduct != null) {
            List<IComponent> deployedComponents = deployedProduct.getProductStructure().getComponents();
            changedComponents = compareProductStructures(requiredProduct.getProductStructure(),
                    deployedProduct.getProductStructure());
            deploymentPath = deployedProduct.getDeploymentPath();
            deployedComponents = Lists.reverse(deployedComponents);
            res = doCommands(deployedComponents, STOP);
            if (res != OK) return res;
            res = doCommands(changedComponents.getOrDefault(UNDEPLOY, Collections.emptyList()), UNDEPLOY);
            if (res != OK) return res;
        } else {
            changedComponents = compareProductStructures(requiredProduct.getProductStructure(), ProductStructure.createEmptyStructure());
            deploymentPath = requiredProduct.getProductStructure().getDefaultDeploymentPath();
        }
        List<IComponent> componentForDeploy;
        if (!changedComponents.get(DEPLOY).isEmpty()) {
            componentForDeploy = changedComponents.get(DEPLOY);
            res = deployComponents(componentForDeploy);
            if (res != OK) return res;
            res = doCommands(requiredProduct.getProductStructure().getComponents(), START);
            if (res != OK) return res;
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
            if (res != OK) return res;
        }
        return res;
    }

    Map<Command, List<IComponent>> compareProductStructures(IProductStructure requiredPS, IProductStructure deployedPS) {
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

    private DeploymentResult deployComponents(List<IComponent> components) {
        DeploymentResult res = null;
        List<IComponent> deployedComponents = new ArrayList<>();
        for (IComponent component : components) {
            res = applyCommand(component, DEPLOY);
            if (res == FAILED || res == NEED_REBOOT) {
                if (log.isDebugEnabled()) log.debug(component.getArtifactCoords().getArtifactId() + " failed");
                deployedComponents = Lists.reverse(deployedComponents);
                for (IComponent deployedComponent : deployedComponents) {
                    applyCommand(deployedComponent, UNDEPLOY);
                    if (log.isDebugEnabled())
                        log.debug(deployedComponent.getArtifactCoords().getArtifactId() + " successfully undeployed");
                }
                return res;
            }
            if (log.isDebugEnabled())
                log.debug(component.getArtifactCoords().getArtifactId() + " successfully deployed");
            deployedComponents.add(component);
        }
        return res;
    }

    private DeploymentResult deployDependent(IProduct product) throws EIncompatibleApiVersion {
        List<Artifact> dependents = product.getDependentProducts().stream()
                .map(DefaultArtifact::new)
                .collect(Collectors.toList());
        DeploymentResult res = OK;
        for (Artifact dependent : dependents) {
            res = deploy(dependent);
            if (res == FAILED || res == NEED_REBOOT) return res;
        }
        return res;
    }

    @SneakyThrows
    private DeploymentResult applyCommand(IComponent component, Command command) {
        List<IComponentDeployer> deployers = component.getDeploymentProcedure().getComponentDeployers();
        DeploymentResult res;
        List<IComponentDeployer> successfulDeployers = new ArrayList<>();
        for (IComponentDeployer deployer : deployers) {
            DeploymentContext context;
            if (component.getArtifactCoords().getArtifactId().equals("legacyComponent"))
                context = new DeploymentContext("legacyProduct");
            else
                context = downloader.getContextByArtifactId(component.getArtifactCoords().getArtifactId());
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
            if (command == DEPLOY && (res == FAILED || res == NEED_REBOOT)) {
                if (log.isDebugEnabled()) log.debug(deployer.getClass().getSimpleName() + " failed");
                successfulDeployers = Lists.reverse(successfulDeployers);
                for (IComponentDeployer undeployer : successfulDeployers) {
                    undeployer.undeploy();
                    if (log.isDebugEnabled())
                        log.debug(undeployer.getClass().getSimpleName() + " successfully undeployed");
                }
                return res;
            }
            if (log.isDebugEnabled()) log.debug(deployer.getClass().getSimpleName() + " done");
            successfulDeployers.add(deployer);
        }
        return OK;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> listDeployedProducts() {
        return (Map<String, Object>) Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
    }

    enum Command {DEPLOY, UNDEPLOY, STOP, START}
}
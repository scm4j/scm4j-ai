package org.scm4j.deployer.engine;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.scm4j.commons.Version;
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.exceptions.EIncompatibleApiVersion;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.scm4j.deployer.api.DeploymentResult.*;
import static org.scm4j.deployer.engine.Deployer.Command.DEPLOY;
import static org.scm4j.deployer.engine.Deployer.Command.UNDEPLOY;

@Slf4j
@Data
class Deployer {

    Deployer(File portableFolder, File workingFolder, Downloader downloader) {
        this.workingFolder = workingFolder;
        this.portableFolder = portableFolder;
        this.downloader = downloader;
        deployedProductsFile = new File(workingFolder, DEPLOYED_PRODUCTS);
        deployedProducts = new HashMap<>();
    }

    private static final String DEPLOYED_PRODUCTS = "deployed-products.yml";

    private Map<String, List<String>> deployedProducts;
    private final File workingFolder;
    private final File portableFolder;
    private final File deployedProductsFile;
    private final Downloader downloader;
    private File legacyProductFolder;

    @SuppressWarnings("unchecked")
    DeploymentResult doCommand(Artifact art, Command command) throws EIncompatibleApiVersion {
        DeploymentResult res;
        String artifactId = art.getArtifactId();
        Version version = new Version(art.getVersion());
        deployedProducts = Utils.readYml(deployedProductsFile);
        if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).contains(version.toString())) {
            if (command == DEPLOY) {
                log.info(Utils.productName(artifactId, version.toString()).append(" already installed!").toString());
                return ALREADY_INSTALLED;
            }
            downloader.get(art.getGroupId(), artifactId, version.toString(), art.getExtension());
            IProduct product = downloader.getProduct();
            switch (command) {
                case UNDEPLOY:
                    res = undeploy(product, artifactId, version);
                    res.setProduct(product);
                    return res;
                case UPGRADE:
                    res = upgrade(product, artifactId, version);
                    res.setProduct(product);
                    return res;
                default:
                    throw new IllegalArgumentException();
            }
        } else {
            switch (command) {
                case UPGRADE:
                case UNDEPLOY:
                    log.info(Utils.productName(artifactId, version.toString()).append(" doesn't exists!").toString());
                    return FAILED;
                case DEPLOY:
                    downloader.get(art.getGroupId(), artifactId, version.toString(), art.getExtension());
                    IProduct product = downloader.getProduct();
                    res = deploy(product, artifactId, version);
                    res.setProduct(product);
                    return res;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    DeploymentResult undeploy(IProduct product, String artifactId, Version version) {
        StringBuilder productName = Utils.productName(artifactId, version.toString());
        return OK;
    }

    DeploymentResult upgrade(IProduct product, String artifactId, Version version) {
        StringBuilder productName = Utils.productName(artifactId, version.toString());
        return OK;
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    DeploymentResult deploy(IProduct product, String artifactId, Version version) throws EIncompatibleApiVersion {
        StringBuilder productName = Utils.productName(artifactId, version.toString());
        DeploymentResult res;
        ILegacyProduct legacyProduct = null;
        if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).isEmpty() &&
                product instanceof ILegacyProduct && ((ILegacyProduct) product).queryLegacyProduct()) {
            legacyProduct = (ILegacyProduct) product;
            res = checkLegacyProduct(legacyProduct, artifactId, version);
            if (res != OK)
                return res;
        }
        if (!product.getDependentProducts().isEmpty()) {
            res = deployDependent(product);
            if (res != OK)
                return res;
        }
        deployedProducts = Utils.readYml(deployedProductsFile);
        res = installComponents(product, legacyProduct);
        if (res != OK)
            return res;
        deployedProducts.putIfAbsent(artifactId, new ArrayList<>());
        deployedProducts.get(artifactId).add(version.toString());
        log.info(productName.append(" successfully installed!").toString());
        Utils.writeYaml(deployedProducts, deployedProductsFile);
        return res;
    }

    private DeploymentResult installComponents(IProduct product, ILegacyProduct legacyProduct) {
        DeploymentResult res = OK;
        List<IComponent> components = product.getProductStructure().getComponents();
        List<IComponent> deployedComponents = new ArrayList<>();
        for (IComponent component : components) {
            res = applyCommand(component, DEPLOY, legacyProduct);
            res.setProduct(product);
            if (res == FAILED || res == NEED_REBOOT) {
                if (log.isDebugEnabled())
                    log.debug(component.getArtifactCoords().getArtifactId() + " failed");
                deployedComponents = Lists.reverse(deployedComponents);
                for (IComponent deployedComponent : deployedComponents) {
                    applyCommand(deployedComponent, UNDEPLOY, legacyProduct);
                    if (log.isDebugEnabled())
                        log.debug(component.getArtifactCoords().getArtifactId() + " successfully undeployed");
                }
                return res;
            }
            if (log.isDebugEnabled())
                log.debug(component.getArtifactCoords().getArtifactId() + " successfully deployed");
            deployedComponents.add(component);
        }
        return res;
    }

    private DeploymentResult checkLegacyProduct(ILegacyProduct legacyProduct, String artifactId, Version version) {
        StringBuilder productName = Utils.productName(artifactId, version.toString());
        if (legacyProduct.getLegacyVersion().equals(version)) {
            log.info(productName.append(" already installed!").toString());
            return ALREADY_INSTALLED;
        }
        if (legacyProduct.getLegacyVersion().isGreaterThan(version)) {
            log.info(productName.append(" newer version exist").toString());
            return NEWER_VERSION_EXISTS;
        }
        DeploymentResult res = legacyProduct.removeLegacyProduct();
        if (res == NEED_REBOOT) {
            log.info(productName.append(" need reboot to uninstall legacy product").toString());
        }
        if (res == FAILED) {
            log.info(productName.append(" can't delete legacy product").toString());
        }
        return res;
    }

//            if (res == FAILED || res == NEED_REBOOT) {
//        deployedComponents = Lists.reverse(deployedComponents);
//        for (IComponent deployedComponent : deployedComponents) {
//            applyCommand(deployedComponent, UNDEPLOY, legacyProduct);
//        }
//        return res;
//    }
//        deployedComponents.add(component);
//
//        if (res == FAILED || res == NEED_REBOOT) {
//        successfulDeployers = Lists.reverse(successfulDeployers);
//        for (IComponentDeployer undeployer : successfulDeployers) {
//            undeployer.undeploy();
//        }
//        return res;
//    }
//        successfulDeployers.add(deployer);

    private DeploymentResult deployDependent(IProduct product) throws EIncompatibleApiVersion {
        List<Artifact> dependents = product.getDependentProducts().stream()
                .map(DefaultArtifact::new)
                .collect(Collectors.toList());
        DeploymentResult res = OK;
        for (Artifact dependent : dependents) {
            res = doCommand(dependent, DEPLOY);
            if (res == FAILED || res == NEED_REBOOT)
                return res;
        }
        return res;
    }

    @SneakyThrows
    private DeploymentContext initComponent(IComponent comp, ILegacyProduct legacyProduct) {
        String artId = comp.getArtifactCoords().getArtifactId();
        DeploymentContext context = downloader.getDepCtx().get(artId);
        if (legacyProduct == null)
            context.setDeploymentURL(downloader.getProduct().getProductStructure().getDefaultDeploymentURL());
        else
            context.setDeploymentURL(new URL(legacyProduct.getLegacyFile().toURI().toURL().toString()));
        return context;
    }

    @SneakyThrows
    //TODO all components stop's => undeploy => list reverse => deploy => start
    private DeploymentResult applyCommand(IComponent component, Command command, ILegacyProduct legacyProduct) {
        IDeploymentContext context = initComponent(component, legacyProduct);
        List<IComponentDeployer> deployers = component.getDeploymentProcedure().getComponentDeployers();
        DeploymentResult res;
        List<IComponentDeployer> successfulDeployers = new ArrayList<>();
        for (IComponentDeployer deployer : deployers) {
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
                if (log.isDebugEnabled())
                    log.debug(deployer.getClass().getSimpleName() + " failed");
                successfulDeployers = Lists.reverse(successfulDeployers);
                for (IComponentDeployer undeployer : successfulDeployers) {
                    undeployer.undeploy();
                    if (log.isDebugEnabled())
                        log.debug(undeployer.getClass().getSimpleName() + " successfully undeployed");
                }
                return res;
            }
            if (log.isDebugEnabled())
                log.debug(deployer.getClass().getSimpleName() + " done");
            successfulDeployers.add(deployer);
        }
        return OK;
    }

    enum Command {DEPLOY, UNDEPLOY, UPGRADE, STOP, START}

    @SuppressWarnings("unchecked")
    Map<String, Object> listDeployedProducts() {
        return (Map<String, Object>) Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
    }
}
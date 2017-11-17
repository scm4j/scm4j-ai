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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.scm4j.deployer.api.DeploymentResult.*;
import static org.scm4j.deployer.engine.Deployer.Command.DEPLOY;
import static org.scm4j.deployer.engine.Deployer.Command.UNDEPLOY;

@Slf4j
@Data
class Deployer {

    private static final String DEPLOYED_PRODUCTS = "deployed-products.yml";

    private Map<String, List<String>> deployedProducts;
    private final File workingFolder;
    private final File portableFolder;
    private final File deployedProductsFile;
    private final Downloader downloader;
    private File legacyProductFolder;

    Deployer(File portableFolder, File workingFolder, Downloader downloader) {
        this.workingFolder = workingFolder;
        this.portableFolder = portableFolder;
        this.downloader = downloader;
        deployedProductsFile = new File(workingFolder, DEPLOYED_PRODUCTS);
    }

    //TODO divide method
    @SuppressWarnings("unchecked")
    DeploymentResult deploy(Artifact art) throws EIncompatibleApiVersion {
        String artifactId = art.getArtifactId();
        Version version = new Version(art.getVersion());
        deployedProducts = Utils.readYml(deployedProductsFile);
        if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).contains(version.toString())) {
            log.warn(Utils.productName(artifactId, version.toString()).append(" already installed!").toString());
            return ALREADY_INSTALLED;
        } else {
            downloader.get(art.getGroupId(), artifactId, version.toString(), art.getExtension());
            IProduct product = downloader.getProduct();
            return deploy(product, artifactId, version);
        }
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    DeploymentResult deploy(IProduct product, String artifactId, Version version) throws EIncompatibleApiVersion {
        StringBuilder productName = Utils.productName(artifactId, version.toString());
        DeploymentResult res;
        ILegacyProduct legacyProduct = null;
        if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).isEmpty() &&
                product instanceof ILegacyProduct && ((ILegacyProduct) product).queryLegacyProduct()) {
            //TODO delete old product and deploy new
            legacyProduct = (ILegacyProduct) product;
            res = checkLegacyProduct(legacyProduct, artifactId, version);
            if (res != OK)
                return res;
        }
        if (!product.getDependentProducts().isEmpty()) {
            res = deployDependent(product, productName);
            if (res != OK)
                return res;
        }
        deployedProducts = Utils.readYml(deployedProductsFile);
        List<IComponent> components = product.getProductStructure().getComponents();
        List<DeploymentResult> results = new ArrayList<>();
        for (IComponent component : components) {
            String artId = component.getArtifactCoords().getArtifactId();
            DeploymentContext context = downloader.getDepCtx().get(artId);
            if (legacyProduct == null)
                context.setDeploymentURL(downloader.getProduct().getProductStructure().getDefaultDeploymentURL());
            else
                context.setDeploymentURL(new URL(legacyProduct.getLegacyFile().toURI().toURL().toString()));
            results.add(installComponent(component, DEPLOY, context));
        }
        deployedProducts.putIfAbsent(artifactId, new ArrayList<>());
        deployedProducts.get(artifactId).add(version.toString());
        return commonDeploymentResult(results, productName);
    }

    private DeploymentResult checkLegacyProduct(ILegacyProduct legacyProduct, String artifactId, Version version) {
        StringBuilder productName = Utils.productName(artifactId, version.toString());
        if (legacyProduct.getLegacyVersion().equals(version)) {
            log.info(productName.append(" already installed!").toString());
            return ALREADY_INSTALLED;
        }
        if (legacyProduct.getLegacyVersion().isGreaterThan(version)) {
            log.info(productName.append(" newer version exist!").toString());
            return NEWER_VERSION_EXISTS;
        }
        DeploymentResult res = legacyProduct.removeLegacyProduct();
        if (res == NEED_REBOOT) {
            log.info(productName.append(" need reboot to uninstall legacy product!").toString());
        }
        if (res == FAILED) {
            log.info(productName.append(" can't delete legacy product!").toString());
        }
        return res;
    }

    private DeploymentResult deployDependent(IProduct product, StringBuilder productName) throws EIncompatibleApiVersion {
        List<Artifact> dependents = product.getDependentProducts().stream()
                .map(DefaultArtifact::new)
                .collect(Collectors.toList());
        List<DeploymentResult> dependentResults = new ArrayList<>();
        for (Artifact dependent : dependents) {
            DeploymentResult dependentResult = deploy(dependent);
            dependentResults.add(dependentResult);
        }
        DeploymentResult res = commonDeploymentResult(dependentResults, productName);
        if (res == FAILED) {
            log.info(productName.append(" failed, because dependent products installation uncompleted").toString());
        }
        if (res == NEED_REBOOT) {
            log.info(productName.append(" dependent product installed, but need reboot to make installation complete").toString());
        }
        return res;
    }

    @SneakyThrows
    private DeploymentResult installComponent(IComponent component, Command command, IDeploymentContext context) {
        IDeploymentProcedure procedure = component.getDeploymentProcedure();
        List<IComponentDeployer> deployers = procedure.getComponentDeployers();
        if (command == UNDEPLOY)
            deployers = Lists.reverse(deployers);
        List<DeploymentResult> returnCodes = new ArrayList<>();
        for (IComponentDeployer deployer : deployers) {
            deployer.init(context);
            switch (command) {
                case DEPLOY:
                    returnCodes.add(deployer.deploy());
                    break;
                case UNDEPLOY:
                    returnCodes.add(deployer.undeploy());
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
        return returnCodes.stream().anyMatch(res -> res.equals(FAILED)) ?
                FAILED : returnCodes.stream().anyMatch(res -> res.equals(NEED_REBOOT)) ?
                NEED_REBOOT : OK;
    }

    private DeploymentResult commonDeploymentResult(List<DeploymentResult> results, StringBuilder productName) {
        DeploymentResult deploymentResult = results.stream().anyMatch(res -> res.equals(FAILED)) ?
                FAILED : results.stream().anyMatch(res -> res.equals(NEED_REBOOT)) ?
                NEED_REBOOT : OK;
        switch (deploymentResult) {
            case OK:
                log.info(productName.append(" successfully installed!").toString());
                Utils.writeYaml(deployedProducts, deployedProductsFile);
                break;
            case NEED_REBOOT:
                log.info(productName.append(" need reboot before installation ends!").toString());
                Utils.writeYaml(deployedProducts, deployedProductsFile);
                break;
            case FAILED:
                log.info(productName.append(" failed!").toString());
                break;
            default:
                throw new IllegalArgumentException();
        }
        return deploymentResult;
    }

    public enum Command {DEPLOY, UNDEPLOY, UPGRADE, START, STOP}

    @SuppressWarnings("unchecked")
    Map<String, Object> listDeployedProducts() {
        return (Map<String, Object>) Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
    }
}
package org.scm4j.deployer.engine;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.exceptions.EIncompatibleApiVersion;

import java.io.File;
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
        String version = art.getVersion();
        deployedProducts = Utils.readYml(deployedProductsFile);
        if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).contains(version)) {
            log.warn(Utils.productName(artifactId, version).append(" already installed!").toString());
            return ALREADY_INSTALLED;
        } else {
            downloader.get(art.getGroupId(), artifactId, version, art.getExtension());
            IProduct product = downloader.getProduct();
            return deploy(product, artifactId, version);
        }
    }

    @SuppressWarnings("unchecked")
    DeploymentResult deploy(IProduct product, String artifactId, String version) throws EIncompatibleApiVersion {
        StringBuilder productName = Utils.productName(artifactId, version);
        if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).isEmpty() &&
                product instanceof ILegacyProduct && ((ILegacyProduct) product).queryLegacyProduct()) {
            //TODO delete old product and deploy new
            ILegacyProduct legacyProduct = (ILegacyProduct) product;
//                validatelegacyProduct(legacyProduct, version);
            log.info(productName.append(" already installed!").toString());
            return ALREADY_INSTALLED;
        } else {
            if (!product.getDependentProducts().isEmpty()) {
                DeploymentResult dependentResult = deployDependent(product, productName);
                if (dependentResult.equals(FAILED)) {
                    log.info(productName.append(" failed, because dependent products installation uncompleted").toString());
                    return FAILED;
                }
            }
            deployedProducts = Utils.readYml(deployedProductsFile);
        }
        List<IComponent> components = product.getProductStructure().getComponents();
        List<DeploymentResult> results = new ArrayList<>();
        for (IComponent component : components) {
            results.add(installComponent(component, DEPLOY));
        }
        deployedProducts.putIfAbsent(artifactId, new ArrayList<>());
        deployedProducts.get(artifactId).add(version);
        return commonDeploymentResult(results, productName);
    }

    @SneakyThrows
    DeploymentResult installComponent(IComponent component, Command command) {
        IDeploymentProcedure procedure = component.getDeploymentProcedure();
        String artifactId = component.getArtifactCoords().getArtifactId();
        DeploymentContext context = downloader.getDepCtx().get(artifactId);
        context.setDeploymentURL(downloader.getProduct().getProductStructure().getDefaultDeploymentURL());
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

    private DeploymentResult deployDependent(IProduct product, StringBuilder productName) throws EIncompatibleApiVersion {
        List<Artifact> dependents = product.getDependentProducts().stream()
                .map(DefaultArtifact::new)
                .collect(Collectors.toList());
        List<DeploymentResult> dependentResults = new ArrayList<>();
        for (Artifact dependent : dependents) {
            DeploymentResult dependentResult = deploy(dependent);
            dependentResults.add(dependentResult);
        }
        return commonDeploymentResult(dependentResults, productName);
    }

    public enum Command {DEPLOY, UNDEPLOY, UPGRADE, START, STOP}

//    private DeploymentResult validatelegacyProduct(ILegacyProduct legacyProduct, String version) {
//        if(legacyProduct.getLegacyVersion().equals(new Version(version)))
//            return DeploymentResult.ALREADY_INSTALLED;
//        if(legacyProduct.getLegacyVersion().isGreaterThan(new Version(version)))
//            return DeploymentResult.NEWER_VERSION_EXISTS;
//        legacyProductFolder = legacyProduct.getLegacyFile();
//    }

    @SuppressWarnings("unchecked")
    Map<String, Object> listDeployedProducts() {
        return (Map<String, Object>) Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
    }
}
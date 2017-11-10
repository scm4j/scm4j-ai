package org.scm4j.deployer.engine;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.scm4j.deployer.api.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
class Deployer {

    private static final String DEPLOYED_PRODUCTS = "deployed-products.yml";

    private final File workingFolder;
    private final File portableFolder;
    private final File deployedProductsFolder;
    private final Downloader downloader;

    Deployer(File portableFolder, File workingFolder, Downloader downloader) {
        this.workingFolder = workingFolder;
        this.portableFolder = portableFolder;
        this.downloader = downloader;
        deployedProductsFolder = new File(workingFolder, DEPLOYED_PRODUCTS);
    }

    //TODO divide method
    @SuppressWarnings("unchecked")
    DeploymentResult deploy(String groupId, String artifactId, String version, String extention) {
        Map<String, List<String>> deployedProducts = Utils.readYml(deployedProductsFolder);
        StringBuilder productName = new StringBuilder().append(artifactId).append("-").append(version);
        if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).contains(version)) {
            log.warn(productName.append(" already installed!").toString());
            return DeploymentResult.ALREADY_INSTALLED;
        } else {
            downloader.get(groupId, artifactId, version, extention);
            IProduct product = downloader.getProduct();
            if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).isEmpty() &&
                    product instanceof ILegacyProduct && ((ILegacyProduct) product).queryLegacyProduct()) {
                //TODO delete old product and deploy new
                ILegacyProduct legacyProduct = (ILegacyProduct) product;
//                validatelegacyProduct(legacyProduct, version);
                log.info(productName.append(" already installed!").toString());
                return DeploymentResult.ALREADY_INSTALLED;
            } else {
                if (!product.getDependentProducts().isEmpty()) {
                    List<String> dependents = product.getDependentProducts();
                    for (String dependent : dependents) {
                        Artifact depArt = new DefaultArtifact(dependent);
                        deploy(depArt.getGroupId(), depArt.getArtifactId(), depArt.getVersion(), depArt.getExtension());
                    }
                    deployedProducts = Utils.readYml(deployedProductsFolder);
                }
                List<IComponent> components = product.getProductStructure().getComponents();
                List<Integer> result = new ArrayList<>();
                for (IComponent component : components) {
                    result.add(installComponent(component, Command.DEPLOY));
                }
                if (deployedProducts.get(artifactId) == null) {
                    deployedProducts.put(artifactId, new ArrayList<>());
                    deployedProducts.get(artifactId).add(version);
                } else {
                    deployedProducts.get(artifactId).add(version);
                }
                Utils.writeYaml(deployedProducts, deployedProductsFolder);
                log.info(productName.append(" successfully installed!").toString());
                return result.stream().anyMatch(code -> code != 0) ? DeploymentResult.NEED_REBOOT : DeploymentResult.OK;
            }
        }
    }

    //    private DeploymentResult validatelegacyProduct(ILegacyProduct legacyProduct, String version) {
//        if(legacyProduct.getLegacyVersion().equals(new Version(version)))
//            return DeploymentResult.ALREADY_INSTALLED;
//        if(legacyProduct.getLegacyVersion().isGreaterThan(new Version(version)))
//            return DeploymentResult.NEWER_VERSION_EXISTS;
//
//    }

    @SneakyThrows
    private int installComponent(IComponent component, Command command) {
        IDeploymentProcedure procedure = component.getDeploymentProcedure();
        String artifactId = component.getArtifactCoords().getArtifactId();
        DeploymentContext context = downloader.getDepCtx().get(artifactId);
        context.setDeploymentURL(downloader.getProduct().getProductStructure().getDefaultDeploymentURL());
        List<IAction> actions = procedure.getActions();
        if (command == Command.UNDEPLOY)
            actions = Lists.reverse(actions);
        List<Integer> returnCodes = new ArrayList<>();
        for (IAction action : actions) {
            IComponentDeployer deployer = action.getInstallerClass().newInstance();
            deployer.init(context, action.getParams());
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
        return returnCodes.stream().anyMatch(code -> code != 0) ? 1 : 0;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> listDeployedProducts() {
        return (Map<String, Object>) Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
    }
}

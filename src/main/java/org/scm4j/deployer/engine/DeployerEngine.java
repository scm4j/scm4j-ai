package org.scm4j.deployer.engine;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.exceptions.ENoMetadata;

import java.io.File;
import java.util.*;

@Data
@Slf4j
public class DeployerEngine implements IProductDeployer {

    private static final String DEPLOYED_PRODUCTS = "deployed-products.yml";

    private final File workingFolder;
    private final File portableFolder;
    private final String productListArtifactoryUrl;
    private final DeployerRunner runner;
    private final File deployedProductsFolder;

    public DeployerEngine(File portableFolder, File workingFolder, String productListArtifactoryUrl) {
        if (portableFolder == null)
            portableFolder = workingFolder;
        this.workingFolder = workingFolder;
        this.portableFolder = portableFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
        deployedProductsFolder = new File(workingFolder, DEPLOYED_PRODUCTS);
        this.runner = new DeployerRunner(portableFolder, workingFolder, productListArtifactoryUrl);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DeploymentResult deploy(String artifactId, String version) {
        Map<String, List<String>> deployedProducts = Utils.readYml(deployedProductsFolder);
        StringBuilder productName = new StringBuilder().append(artifactId).append("-").append(version);
        if (deployedProducts.getOrDefault(artifactId, new ArrayList<>()).contains(version)) {
            log.warn(productName.append(" already installed!").toString());
            return DeploymentResult.ALREADY_INSTALLED;
        } else {
            download(artifactId, version);
            IProduct product = runner.getProduct();
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
                        deploy(depArt.getArtifactId(), depArt.getVersion());
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

    @Override
    public File download(String artifactId, String version) {
        String groupId = Utils.getGroupId(runner, artifactId);
        Artifact artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
        return runner.get(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                artifact.getExtension());
    }

    @Override
    public DeploymentResult undeploy(String artifactId, String version) {
        return DeploymentResult.OK;
    }

    @Override
    public DeploymentResult upgrade(String artifactId, String version) {
        return DeploymentResult.OK;
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public List<String> listProducts() {
        Map<String, Map<String, String>> entry = runner.getProductList().readFromProductList();
        return new ArrayList<>(entry.get(ProductList.PRODUCTS).values());
    }

    @Override
    @SneakyThrows
    public List<String> refreshProducts() {
        runner.getProductList().downloadProductList();
        return listProducts();
    }

    @Override
    public Map<String, Boolean> listProductVersions(String artifactId) {
        Optional<Set<String>> versions = Optional.ofNullable
                (runner.getProductList().readProductVersions(artifactId).get(artifactId));
        Map<String, Boolean> downloadedVersions = new LinkedHashMap<>();
        versions.ifPresent(strings -> strings.forEach
                (version -> downloadedVersions.put(version, versionExists(artifactId, version))));
        return downloadedVersions;
    }

    private boolean versionExists(String artifactId, String version) {
        String groupId = Utils.getGroupId(runner, artifactId);
        File productVersionFolder = new File(runner.getWorkingRepository(), Utils.coordsToFolderStructure(groupId, artifactId, version));
        return productVersionFolder.exists();
    }

    @Override
    public Map<String, Boolean> refreshProductVersions(String artifactId) {
        try {
            runner.getProductList().refreshProductVersions(Utils.getGroupId(runner, artifactId), artifactId);
        } catch (ENoMetadata e) {
            throw new RuntimeException();
        }
        return listProductVersions(artifactId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> listDeployedProducts() {
        return (Map<String, Object>) Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
    }

    @SneakyThrows
    private int installComponent(IComponent component, Command command) {
        IDeploymentProcedure procedure = component.getDeploymentProcedure();
        String artifactId = component.getArtifactCoords().getArtifactId();
        DeploymentContext context = runner.getDepCtx().get(artifactId);
        context.setDeploymentURL(runner.getProduct().getProductStructure().getDefaultDeploymentURL());
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
}


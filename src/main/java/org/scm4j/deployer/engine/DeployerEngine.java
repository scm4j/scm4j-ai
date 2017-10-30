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
    private static final String INSTALLERS_JAR_NAME = "scm4j-deployer-installers";

    private final File workingFolder;
    private final File portableFolder;
    private final String productListArtifactoryUrl;
    private final DeployerRunner runner;
    private final File deployedProductsFolder;

    public DeployerEngine(File flashFolder, File workingFolder, String productListArtifactoryUrl) {
        if (flashFolder == null)
            flashFolder = workingFolder;
        this.workingFolder = workingFolder;
        this.portableFolder = flashFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
        deployedProductsFolder = new File(workingFolder,DEPLOYED_PRODUCTS);
        this.runner = new DeployerRunner(flashFolder, workingFolder, productListArtifactoryUrl);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void deploy(String artifactId, String version) {
        Map<String,List<String>> deployedProducts = Utils.readYml(deployedProductsFolder);
        StringBuilder productName = new StringBuilder().append(artifactId).append("-").append(version);
        if(deployedProducts.getOrDefault(artifactId, new ArrayList<>()).contains(version)) {
            log.warn(productName.append(" already installed!").toString());
        } else {
            File productFile = download(artifactId, version);
            IProduct product = runner.getProduct(productFile);
            if(product.isInstalled(artifactId)) {
                log.warn(productName.append(" already installed!").toString());
            } else {
                if (!product.getDependentProducts().isEmpty()) {
                    List<String> dependents = product.getDependentProducts();
                    for(String dependent : dependents) {
                        Artifact depArt = new DefaultArtifact(dependent);
                        deploy(depArt.getArtifactId(), depArt.getVersion());
                    }
                    deployedProducts = Utils.readYml(deployedProductsFolder);
                }
                File installersJar = runner.getDepCtx().get(artifactId).getArtifacts().get(INSTALLERS_JAR_NAME);
                //TODO check existing product and copy from flash to local machine
                List<IComponent> components = product.getProductStructure().getComponents();
                for (IComponent component : components) {
                    installComponent(component, Command.DEPLOY, installersJar);
                }
                if (deployedProducts.get(artifactId) == null) {
                    deployedProducts.put(artifactId, new ArrayList<>());
                    deployedProducts.get(artifactId).add(version);
                } else {
                    deployedProducts.get(artifactId).add(version);
                }
                Utils.writeYaml(deployedProducts, deployedProductsFolder);
                log.info(productName.append(" successfully installed!").toString());
            }
        }
    }

    @Override
    public File download(String artifactId, String version) {
        String groupId = Utils.getGroupId(runner, artifactId);
        Artifact artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
        return runner.get(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                artifact.getExtension());
    }

    @Override
    public void undeploy(String artifactId, String version) {

    }

    @Override
    public void upgrade(String artifactId, String version) {
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public List<String> listProducts() {
        Map<String, Map<String,String>> entry = runner.getProductList().readFromProductList();
        return new ArrayList<>(entry.get(ProductList.PRODUCTS).values());
    }

    @Override
    @SneakyThrows
    public List<String> refreshProducts() {
        runner.getProductList().downloadProductList();
        return listProducts();
    }

    //TODO sort result
    @Override
    public Map<String, Boolean> listProductVersions(String artifactId) {
        Optional<Set<String>> versions = Optional.ofNullable
                (runner.getProductList().readProductVersions(artifactId).get(artifactId));
        Map<String, Boolean> downloadedVersions = new LinkedHashMap<>();
        versions.ifPresent(strings -> strings.forEach
                (version -> downloadedVersions.put(version, versionExists(artifactId, version))));
        return downloadedVersions;
    }

    private Boolean versionExists(String artifactId, String version) {
        String groupId = Utils.getGroupId(runner, artifactId);
        File productVersionFolder = new File(runner.getRepository(), Utils.coordsToFolderStructure(groupId, artifactId, version));
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
    public Map listDeployedProducts() {
        return Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
    }

    @SneakyThrows
    private void installComponent(IComponent component, Command command, File installerFile) {
        IDeploymentProcedure procedure = component.getDeploymentProcedure();
        String artifactId = component.getArtifactCoords().getArtifactId();
        DeploymentContext context = runner.getDepCtx().get(artifactId);
        List<IAction> actions = procedure.getActions();
        if (command == Command.UNDEPLOY)
            actions = Lists.reverse(actions);
        for (IAction action : actions) {
            Object obj = Utils.createClassFromJar(installerFile, action.getInstallerClassName());
            if (obj instanceof IComponentDeployer) {
                IComponentDeployer deployer = (IComponentDeployer) obj;
                deployer.init(context, action.getParams());
                switch (command) {
                    case DEPLOY:
                        deployer.deploy();
                        break;
                    case UNDEPLOY:
                        deployer.undeploy();
                        break;
                    case UPGRADE:
                        break;
                }
            }
        }
    }
}

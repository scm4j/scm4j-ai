package org.scm4j.deployer.engine;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.scm4j.deployer.api.*;
import org.scm4j.deployer.engine.exceptions.ENoMetadata;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Data
@Slf4j
public class DeployerEngine implements IProductDeployer {

    enum Command {DEPLOY, UNDEPLOY, UPGRADE}

    private static final String DEPLOYED_PRODUCTS = "deployed-products.yml";

    private final File workingFolder;
    private final File flashFolder;
    private final String productListArtifactoryUrl;
    private final DeployerRunner runner;

    public DeployerEngine(File flashFolder, File workingFolder, String productListArtifactoryUrl) {
        if (flashFolder == null)
            flashFolder = workingFolder;
        this.workingFolder = workingFolder;
        this.flashFolder = flashFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
        this.runner = new DeployerRunner(flashFolder, workingFolder, productListArtifactoryUrl);
    }

    @Override
    public void deploy(String artifactId, String version) {
        File deployedProductsFolder = new File(workingFolder, DEPLOYED_PRODUCTS);
        Map<String, Set<String>> deployedProducts = Utils.readYml(deployedProductsFolder);
        if(deployedProducts.getOrDefault(artifactId, new HashSet<>()).contains(version)) {
            log.trace(artifactId + "-" + version + " already installed!");
        } else {
            File productFile = download(artifactId, version);
            IProduct product = runner.getProduct(productFile);
            File installersJar = runner.getDepCtx().get(artifactId).getArtifacts().get("scm4j-deployer-installers");
            //TODO check existing product and copy from flash to local machine
            //TODO deployDependent()
            List<IComponent> components = product.getProductStructure().getComponents();
            for (IComponent component : components) {
                installComponent(component, Command.DEPLOY, installersJar);
            }
            deployedProducts.getOrDefault(artifactId, Collections.emptySet()).add(version);
            Utils.writeYaml(deployedProducts, deployedProductsFolder);
        }
    }

    private void deployDependent(IProduct product, Map<String, Set<String>> deployedProducts) {
        if(!product.getDependentProducts().isEmpty()) {
            List<String> dependents = product.getDependentProducts();
            for(String dep : dependents) {
                String[] artIdPlusVers = dep.split("-");
                if(deployedProducts.getOrDefault(artIdPlusVers[0], new HashSet<>()).contains(artIdPlusVers[1])) {
                    continue;
                } else {
                    File productFile = download(artIdPlusVers[0], artIdPlusVers[1]);
                }
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
    public List<String> listProducts() {
        return runner.getProductList().readFromProductList().get(ProductList.PRODUCTS).stream()
                .map(s -> StringUtils.substringAfter(s, ":"))
                .collect(Collectors.toList());
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
    public Map<String, Set<String>> listDeployedProducts() {
        return Utils.readYml(new File(workingFolder, DEPLOYED_PRODUCTS));
    }

    @SneakyThrows
    private void installComponent(IComponent component, Command command, File installerFile) {
        IInstallationProcedure procedure = component.getInstallationProcedure();
        Map<String, Map<String, Object>> params = procedure.getActionsParams();
        String artifactId = component.getArtifactCoords().getArtifactId();
        DeploymentContext context = runner.getDepCtx().get(artifactId);
        context.setParams(params);
        List<IAction> actions = procedure.getActions();
        if (command == Command.UNDEPLOY)
            actions = Lists.reverse(actions);
        for (IAction action : actions) {
            Object obj = Utils.loadClassFromJar(installerFile, action.getInstallerClassName());
            if (obj instanceof IComponentDeployer) {
                IComponentDeployer installer = (IComponentDeployer) obj;
                installer.init(context);
                switch (command) {
                    case DEPLOY:
                        installer.deploy();
                        break;
                    case UNDEPLOY:
                        installer.undeploy();
                        break;
                    case UPGRADE:
                        break;
                }
            }
        }
    }
}

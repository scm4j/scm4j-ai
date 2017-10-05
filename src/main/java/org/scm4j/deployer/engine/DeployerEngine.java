package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.scm4j.deployer.api.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class DeployerEngine implements IProductDeployer {

    private final File workingFolder;
    private final String productListArtifactoryUrl;
    private final DeployerRunner runner;

    public DeployerEngine(File workingFolder, String productListArtifactoryUrl) {
        this.workingFolder = workingFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
        runner = new DeployerRunner(workingFolder, productListArtifactoryUrl);
    }

    @Override
    public void deploy(String productCoords) {
        Artifact artifact = new DefaultArtifact(productCoords);
        File product = download(productCoords);
        List<IComponent> components = runner.getProductStructure(product).getComponents();
        for(IComponent component : components) {
            try {
                installComponent(component);
            } catch (Exception e) {
                System.err.println("Can't install component " + component.getArtifactCoords().getArtifactId());
                e.printStackTrace();
            }
        }
    }

    @Override
    public File download(String productCoords) {
        Artifact artifact = new DefaultArtifact(productCoords);
        return runner.get(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                artifact.getExtension());
    }

    @Override
    public void undeploy(String productCoors) {

    }

    @Override
    public void upgrade(String newProductCoords) {
    }

    @Override
    public List<String> listProducts() {
        getRunner().getProductList().readFromProductList();
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

    @Override
    public Map<String, Boolean> listProductVersions(String artifactId) {
        String groupId = Utils.getGroupId(runner, artifactId);
        List<String> versions =  runner.getProductList().readProductVersions(groupId,artifactId).get(artifactId);
        Map<String, Boolean> downloadedVersions = new LinkedHashMap<>();
        versions.forEach( version -> downloadedVersions.put(version, versionExists(groupId, artifactId, version)));
        return downloadedVersions;
    }

    private Boolean versionExists(String groupId, String artifactId, String version) {
        File productVersionFolder = new File(runner.getRepository(), Utils.coordsToFolderStructure(groupId, artifactId, version));
        return productVersionFolder.exists();
    }

    @Override
    public Map<String, Boolean> refreshProductVersions(String artifactId) {
        runner.getProductList().refreshProductVersions(Utils.getGroupId(runner, artifactId),artifactId);
        return listProductVersions(artifactId);
    }

    @Override
    public List<String> listDeployedProducts() {
        return null;
    }

    @SneakyThrows
    private void installComponent(IComponent component) {
        IInstallationProcedure procedure = component.getInstallationProcedure();
        Map<Class, Map<String, Object>> params = procedure.getActionsParams();
        String artifactId = component.getArtifactCoords().getArtifactId();
        DeploymentContext context = runner.getDepCtx().get(artifactId);
        context.setParams(params);
        for (IAction action : procedure.getActions()) {
            Object obj = action.getInstallerClass().newInstance();
            if (obj instanceof IComponentDeployer) {
                IComponentDeployer installer = (IComponentDeployer) obj;
                installer.init(context);
                installer.deploy();
            }
        }
    }
}

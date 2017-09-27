package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.commons.Coords;
import org.scm4j.deployer.api.*;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class DeployerEngine implements IProductDeployer {

    private final File workingFolder;
    private final String productListArtifactoryUrl;
    private final AIRunner runner;

    public DeployerEngine(File workingFolder, String productListArtifactoryUrl) {
        this.workingFolder = workingFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
        runner = new AIRunner(workingFolder, productListArtifactoryUrl, null, null);
    }

    @Override
    public void deploy(String productCoords) {
        Coords coords = new Coords(productCoords);
        File jarFile = runner.get(coords.getGroupId(), coords.getArtifactId(), coords.getVersion().toString(),
                StringUtils.remove(coords.getExtension(), "@"));
    }

    @Override
    public void undeploy(String productCoors) {

    }

    @Override
    public void upgrade(String newProductCoords) {
    }

    @Override
    public List<String> listAvailableProducts() {
        return runner.getProductList().getProducts().stream()
                .map(s -> StringUtils.substringAfter(s, ":"))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listAvailableProductVersions(String artifactId) {
        String groupId = runner.getProductList().getProducts().stream()
                .filter(s -> s.contains(artifactId))
                .limit(1)
                .collect(Collectors.toList())
                .get(0);
        return runner.getProductList().getProductVersions(StringUtils.substringBefore(groupId, ":"), artifactId);
    }

    public List<String> listDownloadedProducts() {
        return runner.getProductList().getProductListEntry().get(ProductList.DOWNLOADED_PRODUCTS);
    }

    @Override
    public List<String> listInstalledProducts() {
        return null;
    }

    private List<IInstallationProcedure> getInstallationProcedures(File productFile) throws Exception {
        return runner.getProductStructure(productFile).getComponents().stream()
                .map(IComponent::getInstallationProcedure)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private void installComponent(IInstallationProcedure procedure, File jarFile) {
        for(IAction action : procedure.getActions()){
            Object obj = action.getInstallerClass().newInstance();
            if(obj instanceof IComponentDeployer) {
                IComponentDeployer installer = (IComponentDeployer) obj;
                installer.deploy();
            }
        }
    }
}

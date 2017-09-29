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
    private final DeployerRunner runner;

    public DeployerEngine(File workingFolder, String productListArtifactoryUrl) {
        this.workingFolder = workingFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
        runner = new DeployerRunner(workingFolder, productListArtifactoryUrl);
    }

    @Override
    public void deploy(String productCoords) {
        Coords coords = new Coords(productCoords);
        File jarFile;
        if (coords.getExtension().equals(""))
            jarFile = runner.get(coords.getGroupId(), coords.getArtifactId(), coords.getVersion().toString(),
                    ".jar");
        else
            jarFile = runner.get(coords.getGroupId(), coords.getArtifactId(), coords.getVersion().toString(),
                    StringUtils.remove(coords.getExtension(), "@"));
    }

    @Override
    public void undeploy(String productCoors) {

    }

    @Override
    public void upgrade(String newProductCoords) {
    }

    @Override
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

    @Override
    public List<String> listProductVersions(String artifactId) {
        return runner.getProductList().readProductVersions(Utils.getGroupId(runner, artifactId),artifactId).get(artifactId);
    }

    @Override
    public List<String> refreshProductVersions(String artifactId) {
        runner.getProductList().refreshProductVersions(Utils.getGroupId(runner, artifactId),artifactId);
        return listProductVersions(artifactId);
    }

    @Override
    public List<String> listDeployedProducts() {
        return null;
    }

    private List<IInstallationProcedure> getInstallationProcedures(File productFile) throws Exception {
        return runner.getProductStructure(productFile).getComponents().stream()
                .map(IComponent::getInstallationProcedure)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private void installComponent(IInstallationProcedure procedure, File jarFile) {
        for (IAction action : procedure.getActions()) {
            Object obj = action.getInstallerClass().newInstance();
            if (obj instanceof IComponentDeployer) {
                IComponentDeployer installer = (IComponentDeployer) obj;
                installer.deploy();
            }
        }
    }
}

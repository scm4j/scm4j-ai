package org.scm4j.deployer.engine;

import lombok.SneakyThrows;
import org.scm4j.deployer.api.IAction;
import org.scm4j.deployer.api.IComponent;
import org.scm4j.deployer.api.IInstallationProcedure;
import org.scm4j.commons.Coords;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Collectors;
import org.scm4j.deployer.installers.IAI;
import org.scm4j.deployer.installers.IDeployer;

public class AI implements IAI {

    private File workingFolder;
    private String productListArtifactoryUrl;
    private AIRunner runner;

    public AI(File workingFolder, String productListArtifactoryUrl) {
        this.workingFolder = workingFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
    }

    @Override
    public void install(String productCoords) {
        runner = new AIRunner(workingFolder, productListArtifactoryUrl, null, null);
        Coords coords = new Coords(productCoords);
        File jarFile = runner.get(coords.getGroupId(), coords.getArtifactId(), coords.getVersion().toString(),
                coords.getExtension());
        String installerClassName = Utils.getExportedClassName(jarFile);
        if (installerClassName == null) {
            throw new RuntimeException("Installer class name is not located within jar");
        }
        try {
            Class<?> installerClass = Class.forName(installerClassName);
            Constructor<?> constructor = installerClass.getConstructor();
            Object result = constructor.newInstance();
            IDeployer installer;
            if (result.getClass().isAssignableFrom(IDeployer.class)) {
                installer = (IDeployer) result;
            } else {
                throw new RuntimeException("Provided " + installerClassName + " does not implements IInstaller");
            }
            //

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(installerClassName + " class not found");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(installerClassName + " class has no constructor");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void uninstall(String productCoors) {

    }

    @Override
    public void upgrade(String newProductCoords) {
    }

    private List<IInstallationProcedure> getInstallationProcedures(File productFile) throws Exception {
        return runner.getProductStructure(productFile).getComponents().stream()
                .map(IComponent::getInstallationProcedure)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private void installComponent(IInstallationProcedure procedure, File jarFile) {
        for(IAction action : procedure.getActions()){
            Class<?> installerClassName = action.getInstallerClass();
            Object obj = installerClassName.newInstance();
            if(obj instanceof IDeployer) {
                IDeployer installer = (IDeployer) obj;
                installer.deploy();
            }
        }
    }
}

package org.scm4j.deployer.engine;

import org.scm4j.deployer.api.*;

import java.util.ArrayList;
import java.util.List;

public class ProductStructureData {

    private static ProductStructure productStructure;

    private static void enterProduct() {
        IAction action = new Action("ExeRunner");
        List<IAction> actions = new ArrayList<>();
        actions.add(action);
        IInstallationProcedure installationProcedure = new InstallationProcedure(actions);
        IComponent component = new Component("eu.untill:UBL:war:22.2", installationProcedure);
        List<IComponent> components = new ArrayList<>();
        components.add(component);
        productStructure = new ProductStructure(components);
    }

    public static IProductStructure getProductStructure() {
        enterProduct();
        return productStructure;
    }
}

package org.scm4j.ai;

import org.scm4j.ai.api.*;

import java.util.ArrayList;
import java.util.List;

public class ProductStructureData {

    public static ProductStructure productStructure;

    private static void enterProduct() {
        IAction action = new Action("ExeRunner");
        List<IAction> actions = new ArrayList<>();
        actions.add(action);
        IInstallationProcedure installationProcedure = new InstallationProcedure(actions);
        IComponent component = new Component("eu.untill:UBL:22.2:.jar", installationProcedure);
        List<IComponent> components = new ArrayList<>();
        components.add(component);
        productStructure = new ProductStructure(components);
    }

    public static IProductStructure getProductStructure() {
        enterProduct();
        return productStructure;
    }
}

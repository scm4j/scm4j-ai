package org.scm4j.ai.api;

import java.util.List;

public class ProductStructure implements IProductStructure {

    private List<IComponent> components;

    public ProductStructure(List<IComponent> components) {
        this.components = components;
    }

    @Override
    public List<IComponent> getComponents() {
        return null;
    }
}

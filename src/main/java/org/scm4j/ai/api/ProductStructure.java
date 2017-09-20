package org.scm4j.ai.api;

import lombok.Data;

import java.util.List;

@Data
public class ProductStructure implements IProductStructure {

    final private List<IComponent> components;

}

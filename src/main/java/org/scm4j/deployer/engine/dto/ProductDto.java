package org.scm4j.deployer.engine.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductDto {

    private String productFileName;
    private List<String> versions = new ArrayList<>();

}

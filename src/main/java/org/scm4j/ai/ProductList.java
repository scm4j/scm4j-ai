package org.scm4j.ai;

import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.*;

@Data
public class ProductList {

    private ArtifactoryReader productListReader;
    private List<ArtifactoryReader> repos = new ArrayList<>();
    private Set<String> products = new HashSet<>();
    private File localRepo;
    private File localProductList;

    public static final String PRODUCT_LIST_GROUP_ID = "org.scm4j.ai";
    public static final String PRODUCT_LIST_ARTIFACT_ID = "product-list";
    public static final String REPOSITORIES = "repositories";
    public static final String PRODUCTS = "products";

    public ProductList(File localRepo) {
        this.localRepo = localRepo;
    }

    public void downloadProductList(String productListArtifactoryUrl, String userName, String password) throws Exception {
        productListReader = new ArtifactoryReader(productListArtifactoryUrl, userName, password);
        String productListReleaseVersion = productListReader.getProductListReleaseVersion();
        URL remoteProductListUrl = productListReader.getProductUrl(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
                productListReleaseVersion, ".yml");
        localProductList = new File(localRepo, Utils.coordsToRelativeFilePath(PRODUCT_LIST_GROUP_ID, PRODUCT_LIST_ARTIFACT_ID,
                productListReleaseVersion, ".yml"));
        if (!localProductList.exists()) {
            localProductList.getParentFile().mkdirs();
            localProductList.createNewFile();
        }
        FileUtils.copyURLToFile(remoteProductListUrl, localProductList);
        getProductListEntry();
    }

    //TODO change remote repositories on local repo after downloading

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public void getProductListEntry(){
        Map<String, ArrayList<String>> productListEntry;
        @Cleanup
        FileReader reader = new FileReader(localProductList);
            Yaml yaml = new Yaml();
            productListEntry = yaml.loadAs(reader, HashMap.class);
        List<String> reposNames = productListEntry.get(REPOSITORIES);
        for(String repoName: reposNames) {
            repos.add(ArtifactoryReader.getByUrl(repoName));
        }
        products.addAll(productListEntry.get(PRODUCTS));
    }

    public boolean hasProduct(String groupId, String artifactId) throws Exception {
        return getProducts().contains(Utils.coordsToString(groupId, artifactId));
    }
}

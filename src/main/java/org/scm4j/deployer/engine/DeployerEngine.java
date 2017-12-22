package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.SneakyThrows;
import org.eclipse.aether.artifact.Artifact;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IProductDeployer;
import org.scm4j.deployer.engine.exceptions.EIncompatibleApiVersion;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Data
public class DeployerEngine implements IProductDeployer {

    private final File workingFolder;
    private final File portableFolder;
    private final String productListArtifactoryUrl;
    private final Downloader downloader;
    private final Deployer deployer;

    public DeployerEngine(File portableFolder, File workingFolder, String productListArtifactoryUrl) {
        if (portableFolder == null)
            portableFolder = workingFolder;
        this.workingFolder = workingFolder;
        this.portableFolder = portableFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
        this.downloader = new Downloader(portableFolder, workingFolder, productListArtifactoryUrl);
        this.deployer = new Deployer(workingFolder, downloader);
    }

    @SneakyThrows
    @Override
    public DeploymentResult deploy(String artifactId, String version) {
        listProducts();
        Artifact artifact = Utils.initializeArtifact(downloader, artifactId, version);
        try {
            return deployer.deploy(artifact);
        } catch (EIncompatibleApiVersion e) {
            return DeploymentResult.INCOMPATIBLE_API_VERSION;
        }
    }

    @Override
    public File download(String artifactId, String version) {
        listProducts();
        Artifact artifact = Utils.initializeArtifact(downloader, artifactId, version);
        try {
            return downloader.getProductWithDependency(artifact.toString());
        } catch (EIncompatibleApiVersion e) {
            throw new EIncompatibleApiVersion("Api versions are incompatible");
        }
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public List<String> listProducts() {
        Map<String, Map<String, String>> entry = downloader.getProductList().readFromProductList();
        return new ArrayList<>(entry.get(ProductList.PRODUCTS).values());
    }

    @Override
    @SneakyThrows
    public List<String> refreshProducts() {
        downloader.getProductList().downloadProductList();
        return listProducts();
    }

    @Override
    public Map<String, Boolean> listProductVersions(String artifactId) {
        Optional<Set<String>> versions = Optional.ofNullable
                (downloader.getProductList().readProductVersions(artifactId).get(artifactId));
        Map<String, Boolean> downloadedVersions = new LinkedHashMap<>();
        versions.ifPresent(strings -> strings.forEach
                (version -> downloadedVersions.put(version, versionExists(artifactId, version))));
        return downloadedVersions;
    }

    private boolean versionExists(String artifactId, String version) {
        String groupId = Utils.getGroupId(downloader, artifactId);
        File productVersionFolder = new File(downloader.getWorkingRepository(), Utils.coordsToFolderStructure(groupId, artifactId, version));
        return productVersionFolder.exists();
    }

    @Override
    public Map<String, Boolean> refreshProductVersions(String artifactId) {
        try {
            downloader.getProductList().refreshProductVersions(Utils.getGroupId(downloader, artifactId), artifactId);
        } catch (IOException e) {
            throw new RuntimeException("Can't download product versions");
        }
        return listProductVersions(artifactId);
    }

    @Override
    public Map<String, Object> listDeployedProducts() {
        return deployer.listDeployedProducts();
    }
}
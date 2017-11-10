package org.scm4j.deployer.engine;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.artifact.Artifact;
import org.scm4j.deployer.api.DeploymentResult;
import org.scm4j.deployer.api.IProductDeployer;
import org.scm4j.deployer.engine.exceptions.EIncompatibleApiVersion;
import org.scm4j.deployer.engine.exceptions.ENoMetadata;

import java.io.File;
import java.util.*;

@Data
@Slf4j
public class DeployerEngine implements IProductDeployer {

    private final File workingFolder;
    private final File portableFolder;
    private final String productListArtifactoryUrl;
    private final Downloader downloader;
    private final Deployer deployer;
    private final ProductList list;

    public DeployerEngine(File portableFolder, File workingFolder, String productListArtifactoryUrl) {
        if (portableFolder == null)
            portableFolder = workingFolder;
        this.workingFolder = workingFolder;
        this.portableFolder = portableFolder;
        this.productListArtifactoryUrl = productListArtifactoryUrl;
        this.downloader = new Downloader(portableFolder, workingFolder, productListArtifactoryUrl);
        this.deployer = new Deployer(portableFolder, workingFolder, downloader);
        this.list = downloader.getProductList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public DeploymentResult deploy(String artifactId, String version) {
        Artifact artifact = Utils.initializeArtifact(downloader, artifactId, version);
        try {
            return deployer.deploy(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getExtension());
        } catch (EIncompatibleApiVersion e) {
            return DeploymentResult.INCOMPATIBLE_API_VERSION;
        }
    }

    @Override
    @SneakyThrows
    public File download(String artifactId, String version) {
        Artifact artifact = Utils.initializeArtifact(downloader, artifactId, version);
        return downloader.get(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                artifact.getExtension());
    }

    @Override
    public DeploymentResult undeploy(String artifactId, String version) {
        return DeploymentResult.OK;
    }

    @Override
    public DeploymentResult upgrade(String artifactId, String version) {
        return DeploymentResult.OK;
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public List<String> listProducts() {
        Map<String, Map<String, String>> entry = list.readFromProductList();
        return new ArrayList<>(entry.get(ProductList.PRODUCTS).values());
    }

    @Override
    @SneakyThrows
    public List<String> refreshProducts() {
        list.downloadProductList();
        return listProducts();
    }

    @Override
    public Map<String, Boolean> listProductVersions(String artifactId) {
        Optional<Set<String>> versions = Optional.ofNullable
                (list.readProductVersions(artifactId).get(artifactId));
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
            list.refreshProductVersions(Utils.getGroupId(downloader, artifactId), artifactId);
        } catch (ENoMetadata e) {
            throw new RuntimeException();
        }
        return listProductVersions(artifactId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> listDeployedProducts() {
        return deployer.listDeployedProducts();
    }
}


package org.scm4j.deployer.engine;

import lombok.Data;

@Data
public class ProductDescription {

    private long deploymentTime;
    private String deploymentPath;
    private String productVersion;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ProductDescription that = (ProductDescription) o;

        if (!deploymentPath.equals(that.deploymentPath)) return false;
        return productVersion != null ? productVersion.equals(that.productVersion) : that.productVersion == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + deploymentPath.hashCode();
        result = 31 * result + (productVersion != null ? productVersion.hashCode() : 0);
        return result;
    }
}

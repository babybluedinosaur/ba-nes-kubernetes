package org.acme.QueryReconciler;

public class NesQueryStatus {
    // Obligatory, so we can stop certain queries by using their name
    private String deploymentName;

    public String getDeploymentName() {
        return deploymentName;
    }

    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }
}

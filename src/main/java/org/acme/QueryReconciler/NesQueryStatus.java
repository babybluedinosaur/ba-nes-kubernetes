package org.acme.QueryReconciler;

public class NesQueryStatus {
    // Obligatory, so we can stop certain queries by using their name
    private String deploymentName;
    private String phase; // "Completed", "Failed", "Pending"

    public String getDeploymentName() {
        return deploymentName;
    }

    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }

    public String getPhase() { return phase; }

    public void setPhase(String phase) { this.phase = phase; }

}

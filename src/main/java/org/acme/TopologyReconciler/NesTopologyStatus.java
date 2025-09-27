package org.acme.TopologyReconciler;

public class NesTopologyStatus {
    private int workers;
    private int readyWorkers;

    public NesTopologyStatus() {}

    public int getWorkers() {
        return workers;
    }

    public void setWorkers(int workers) {
        this.workers = workers;
    }

    public int getReadyWorkers() {
        return readyWorkers;
    }

    public void setReadyWorkers(int readyWorkers) {
        this.readyWorkers = readyWorkers;
    }
}

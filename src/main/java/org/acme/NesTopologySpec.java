package org.acme;

import java.util.List;

public class NesTopologySpec {

    private List<WorkerSpec> workerSpecs;

    public List<WorkerSpec> getWorkerSpecs() {
        return workerSpecs;
    }

    public void setWorkerSpecs(List<WorkerSpec> workerSpecs) {
        this.workerSpecs = workerSpecs;
    }
}

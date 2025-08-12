package org.acme.TopologyReconciler;

import com.fasterxml.jackson.databind.JsonNode;
import org.acme.TopologyReconciler.Worker.NesWorker;

import java.util.List;

public class NesTopologySpec {

    private List<JsonNode> sinks;
    private List<JsonNode> logicalSources;
    private List<JsonNode> physicalSources;
    private List<NesWorker> workerNodes;

    public List<JsonNode> getSinks() { return sinks; }

    public List<JsonNode> getLogicalSources() {
        return logicalSources;
    }

    public List<JsonNode> getPhysicalSources() { return physicalSources; }

    public List<NesWorker> getWorkerNodes() { return workerNodes; }

    public void setSinks(List<JsonNode> sinks) {
        this.sinks = sinks;
    }

    public void setLogicalSources(List<JsonNode> logicalSources) {
        this.logicalSources = logicalSources;
    }

    public void setPhysicalSources(List<JsonNode> physicalSources) {
        this.physicalSources = physicalSources;
    }

    public void setWorkerNodes(List<NesWorker> workerNodes) {
        this.workerNodes = workerNodes;
    }

}

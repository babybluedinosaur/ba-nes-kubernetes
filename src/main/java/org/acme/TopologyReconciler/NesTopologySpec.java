package org.acme.TopologyReconciler;

import com.fasterxml.jackson.databind.JsonNode;
import org.acme.TopologyReconciler.Worker.NesWorker;

import java.util.List;

public class NesTopologySpec {

    private List<JsonNode> sinks;
    private List<JsonNode> logical;
    private List<JsonNode> physical;
    private List<NesWorker> workers;

    public List<JsonNode> getSinks() { return sinks; }

    public List<JsonNode> getLogical() {
        return logical;
    }

    public List<JsonNode> getPhysical() { return physical; }

    public List<NesWorker> getWorkers() { return workers; }

    public void setSinks(List<JsonNode> sinks) {
        this.sinks = sinks;
    }

    public void setLogical(List<JsonNode> logical) {
        this.logical = logical;
    }

    public void setPhysical(List<JsonNode> physical) {
        this.physical = physical;
    }

    public void setWorkers(List<NesWorker> workers) {
        this.workers = workers;
    }

}

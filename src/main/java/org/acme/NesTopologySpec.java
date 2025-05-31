package org.acme;

import com.fasterxml.jackson.databind.JsonNode;
import org.acme.worker.NesWorker;

import java.util.List;

public class NesTopologySpec {

    private List<NesWorker> workers;
    private List<JsonNode> logical;

    public List<NesWorker> getNodes() {
        return workers;
    }

    public List<JsonNode> getLogical() {
        return logical;
    }

    public void setNodes(List<NesWorker> workers) {
        this.workers = workers;
    }

    public void setLogical(List<JsonNode> logical) { this.logical = logical; }
}

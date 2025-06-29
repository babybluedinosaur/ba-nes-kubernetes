package org.acme.TopologyReconciler.Worker;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

// This class represents the config of a NES worker
public class NesWorker {

    private String name;
    private String image;
    private final String bind = "--bind=0.0.0.0:9090";
    private String connection = "--connection=";
    private int capacity;
    private int buffers;
    private int cpus;
    private List<JsonNode> sinks;
    private Links links;
    private List<JsonNode> physical;


    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public String getBind() {
        return bind;
    }

    public String getConnection() {
        return connection;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBuffers() { return buffers; }

    public int getCpus() { return cpus; }

    public List<JsonNode> getSinks() {
        return sinks;
    }

    public Links getLinks() {
        return links;
    }

    public List<JsonNode> getPhysical() {
        return physical;
    }

    public void print() {
        System.out.println(" - " + name + " : " + image + ":" + capacity);
    }
}

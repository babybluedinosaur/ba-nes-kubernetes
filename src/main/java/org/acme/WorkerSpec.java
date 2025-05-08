package org.acme;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class WorkerSpec {

    private String name;
    private String image;
    private final String data = "--data=0.0.0.0:9090";
    private int capacity;
    private JsonNode sinks;
    private String links;
    private JsonNode physical;


    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public String getData() {
        return data;
    }

    public int getCapacity() {
        return capacity;
    }

    public JsonNode getSinks() {
        return sinks;
    }

    public String getLinks() {
        return links;
    }

    public JsonNode getPhysical() {
        return physical;
    }

    public void print() {
        System.out.println(" - " + name + " : " + image + ":" + capacity);
    }
}

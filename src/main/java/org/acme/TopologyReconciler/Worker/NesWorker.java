package org.acme.TopologyReconciler.Worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// This class represents the config of a NES worker
@JsonIgnoreProperties(ignoreUnknown = true)
public class NesWorker {

    private String host;
    private String image;
    private String executionMode;
    private Integer pageSize;
    private Integer numberOfBuffersInGlobalBufferManager;
    private final String bind = "--bind=0.0.0.0:9090";
    private String connection = "--connection=";


    public String getHost() {
        return host;
    }

    public String getImage() {
        return image;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public Integer getNumberOfBuffersInGlobalBufferManager() {
        return numberOfBuffersInGlobalBufferManager;
    }

    public String getBind() {
        return bind;
    }

    public String getConnection() {
        return connection;
    }

    public void print() {
        System.out.println(" - " + host + " : " + image);
    }
}

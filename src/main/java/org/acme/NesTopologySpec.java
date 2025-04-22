package org.acme;

import io.fabric8.kubernetes.api.model.LimitRangeSpec;

import java.util.List;

public class NesTopologySpec {

    private String image;
    private String logLevel;
    private int replicas;
    private List<String> args;

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }
}

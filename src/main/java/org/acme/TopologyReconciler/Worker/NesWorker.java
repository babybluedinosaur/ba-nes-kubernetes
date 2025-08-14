package org.acme.TopologyReconciler.Worker;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

// This class represents the config of a NES worker
@JsonIgnoreProperties(ignoreUnknown = true)
public class NesWorker {

    private String host;
    private String image;
    private final String bind = "--bind=0.0.0.0:9090";
    private String connection = "--connection=";
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<>();


    public String getHost() {
        return host;
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

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperties(String key, Object value) {
        this.additionalProperties.put(key, value);
    }

    public void print() {
        System.out.println(" - " + host + " : " + image);
    }
}

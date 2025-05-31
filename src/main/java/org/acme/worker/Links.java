package org.acme.worker;

import java.util.ArrayList;
import java.util.List;

public class Links {

    private List<String> downstreams = new ArrayList<>();

    public Links() {
    }

    public List<String> getDownstreams() {
        return downstreams;
    }

    public void setDownstreams(List<String> downstreams) {
        this.downstreams = downstreams;
    }
}

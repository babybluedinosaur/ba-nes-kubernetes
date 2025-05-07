package org.acme;

import java.util.List;

public class WorkerSpec {

    private String name;
    private String image;
    private final String data = "--data=0.0.0.0:9090";
    private int capacity;

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

    public void print() {
        System.out.println(" - " + name + " : " + image + ":" + capacity);
    }
}

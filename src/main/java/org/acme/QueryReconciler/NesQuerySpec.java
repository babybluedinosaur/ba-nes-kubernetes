package org.acme.QueryReconciler;

import org.acme.QueryReconciler.Nebuli.Nebuli;

public class NesQuerySpec {

    private String query;
    private Nebuli nebuli;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Nebuli getNebuli() {
        return nebuli;
    }

    public void setNebuli(Nebuli nebuli) {
        this.nebuli = nebuli;
    }
}

package org.acme.QueryReconciler;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("nebulastream.com")
@Version("v1")
@Kind("NesQuery")
@Plural("nes-queries")
public class NesQuery extends CustomResource<NesQuerySpec,NesQueryStatus> implements Namespaced {

    public NesQuery() {
        super();
    }

    @Override
    public NesQueryStatus getStatus() {
        return super.getStatus();
    }

    @Override
    public void setStatus(NesQueryStatus status) {
        super.setStatus(status);
    }

    @Override
    protected NesQueryStatus initStatus() {
        return super.initStatus();
    }
}

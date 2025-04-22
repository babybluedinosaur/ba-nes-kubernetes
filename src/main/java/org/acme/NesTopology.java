package org.acme;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("nebulastream.com")
@Version("v1")
@Kind("NesTopology")
@Plural("nes-topologies")
public class NesTopology extends CustomResource<NesTopologySpec,NesTopologyStatus> implements Namespaced {
}

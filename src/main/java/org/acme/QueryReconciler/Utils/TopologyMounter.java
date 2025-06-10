package org.acme.QueryReconciler.Utils;

import io.fabric8.kubernetes.api.model.*;

import java.io.IOException;

public class TopologyMounter {

    public static VolumeMount buildTopologyMap(io.fabric8.kubernetes.client.KubernetesClient client) throws IOException {
        return createVolumeMount();
    }

    public static Volume createVolume() {
        Volume volume = new VolumeBuilder()
                .withName("topology-volume")
                .withConfigMap(new ConfigMapVolumeSourceBuilder()
                        .withName("topology-config")
                        .build()
                )
                .build();
        return volume;
    }

    public static VolumeMount createVolumeMount() {
        VolumeMount volumeMount = new VolumeMount();
        volumeMount.setName("topology-volume");
        volumeMount.setMountPath("/topology");
        return volumeMount;
    }
}

package org.acme.QueryReconciler.Utils;

import io.fabric8.kubernetes.api.model.*;

import java.io.IOException;

// This class creates a volume, which contains the output of the TopologyConverter.
// The volume is getting mounted into nebuli
public class TopologyMounter {
    public static VolumeMount buildTopologyMount() throws IOException {
        return createVolumeMount();
    }

    public static Volume createVolume(String topologyConfigMapName) {
        Volume volume = new VolumeBuilder()
                .withName("topology-volume")
                .withConfigMap(new ConfigMapVolumeSourceBuilder()
                        .withName(topologyConfigMapName)
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

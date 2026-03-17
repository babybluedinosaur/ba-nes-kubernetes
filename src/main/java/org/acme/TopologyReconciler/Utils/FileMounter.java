package org.acme.TopologyReconciler.Utils;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;


// This class mounts files into pods, as this is needed for NebulaStream systests
public class FileMounter {
    static String namespace = "default";
    static String nameVolume = "source-data-volume";
    static String pvcName = "source-data-pvc";

    public static void createPVC (KubernetesClient client) {
        if (client.persistentVolumeClaims().inNamespace(namespace).withName(pvcName).get() != null) {
            return;
        }

        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(pvcName)
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", new Quantity("10Gi"))
                .endResources()
                .endSpec()
                .build();

        client.persistentVolumeClaims().inNamespace(namespace).createOrReplace(pvc);
    }

    public static Volume createVolume() {
        return new VolumeBuilder()
                .withName(nameVolume)
                .withPersistentVolumeClaim(
                        new PersistentVolumeClaimVolumeSourceBuilder()
                                .withClaimName(pvcName)
                                .build()
                )
                .build();
    }

    public static VolumeMount createVolumeMount() {
        return new VolumeMountBuilder()
                .withName(nameVolume)
                .withMountPath("/data")
                .build();
    }
}


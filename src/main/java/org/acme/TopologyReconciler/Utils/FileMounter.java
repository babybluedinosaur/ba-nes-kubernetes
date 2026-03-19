package org.acme.TopologyReconciler.Utils;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;


// This class mounts files into pods, as this is needed for NebulaStream systests
public class FileMounter {
    static String namespace = "default";

    static String sourceVolumeName = "source-data-volume";
    static String sourcePvcName = "source-data-pvc";

    static String sinkVolumeName = "sink-volume";
    static String sinkPvcName = "sink-pvc";

    public static void createSourcePVC (KubernetesClient client) {
        if (client.persistentVolumeClaims().inNamespace(namespace).withName(sourcePvcName).get() != null) {
            return;
        }

        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(sourcePvcName)
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

    public static Volume createSourceVolume() {
        return new VolumeBuilder()
                .withName(sourceVolumeName)
                .withPersistentVolumeClaim(
                        new PersistentVolumeClaimVolumeSourceBuilder()
                                .withClaimName(sourcePvcName)
                                .build()
                )
                .build();
    }

    public static VolumeMount createSourceVolumeMount() {
        return new VolumeMountBuilder()
                .withName(sourceVolumeName)
                .withMountPath("/data")
                .build();
    }


    public static void createSinkPVC(KubernetesClient client) {
        if (client.persistentVolumeClaims().inNamespace(namespace).withName(sinkPvcName).get() != null) {
            return;
        }

        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(sinkPvcName)
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", new Quantity("1Gi"))
                .endResources()
                .endSpec()
                .build();

        client.persistentVolumeClaims().inNamespace(namespace).createOrReplace(pvc);
    }

    public static Volume createSinkVolume() {
        return new VolumeBuilder()
                .withName(sinkVolumeName)
                .withPersistentVolumeClaim(
                        new PersistentVolumeClaimVolumeSourceBuilder()
                                .withClaimName(sinkPvcName)
                                .build()
                )
                .build();
    }

    public static VolumeMount createSinkVolumeMount() {
        VolumeMount volumeMount = new VolumeMount();
        volumeMount.setName(sinkVolumeName);
        volumeMount.setMountPath("/sink-output");
        return volumeMount;
    }
}


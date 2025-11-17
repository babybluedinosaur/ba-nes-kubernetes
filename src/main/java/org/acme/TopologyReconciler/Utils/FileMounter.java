package org.acme.TopologyReconciler.Utils;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;


// This class mounts files into pods, as this is needed for NebulaStream systests
public class FileMounter {
    static String namespace = "default";
    static String namePVC = "stream-files-pvc";
    static String nameVolume = "stream-files-volume";


    public FileMounter() {
    }

    public static void createPVC(KubernetesClient client) {
        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(namePVC)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadOnlyMany")
                .withNewResources()
                .addToRequests("storage", new Quantity("1Gi"))
                .endResources()
                .withStorageClassName("standard")
                .endSpec()
                .build();

        client.persistentVolumeClaims().inNamespace(namespace).createOrReplace(pvc);
    }

    public static Volume createVolume() {

        return new VolumeBuilder()
                .withName(nameVolume)
                .withPersistentVolumeClaim(
                        new PersistentVolumeClaimVolumeSourceBuilder()
                                .withClaimName(namePVC)
                                .withReadOnly(false)
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

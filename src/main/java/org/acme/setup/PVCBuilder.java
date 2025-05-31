package org.acme.setup;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;

import java.util.HashMap;
import java.util.Map;

public class PVCBuilder {

    public static void spawnPVC(int quantity, io.fabric8.kubernetes.client.KubernetesClient client) {
        PersistentVolumeClaim pvc = client.persistentVolumeClaims().
                inNamespace("default").withName("queries-pvc").get();

        if (pvc != null) {
            System.out.println("PVC already exists, can not create more.");
            return;
        }

        Map<String, Quantity> requests = new HashMap<>();
        requests.put("storage", new Quantity(quantity + "Gi"));

        PersistentVolumeClaimBuilder pvcBuilder = new PersistentVolumeClaimBuilder();
        pvcBuilder.withNewMetadata().withName("queries-pvc").endMetadata();
        pvcBuilder.withNewSpec().
                addToAccessModes("ReadWriteOnce").
                withNewResources().withRequests(requests).endResources().
                endSpec().
                build();
        client.persistentVolumeClaims().inNamespace("default").create(pvcBuilder.build());
    }
}

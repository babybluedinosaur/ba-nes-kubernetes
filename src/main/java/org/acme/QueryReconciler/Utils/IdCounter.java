package org.acme.QueryReconciler.Utils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;

// Holds the current Query-ID
public class IdCounter {

    String namespace = "default";

    public ConfigMap createCounter (io.fabric8.kubernetes.client.KubernetesClient client) {
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName("query-id")
                .withNamespace(namespace)
                .endMetadata()
                .addToData("counter", "0")
                .build();
        client.configMaps().inNamespace(namespace).createOrReplace(configMap);
        return configMap;
    }
}

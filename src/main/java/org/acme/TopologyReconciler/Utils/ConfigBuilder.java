package org.acme.TopologyReconciler.Utils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ConfigBuilder {

    String namespace = "default";

    // Creates configmap, which contains converted yaml for nebuli input
    public void buildConverterMap(io.fabric8.kubernetes.client.KubernetesClient client) throws IOException {
        String target = Files.readString(Paths.get("src/main/resources/cr/convert-target.yaml"));
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(Map.of("topology", "nes"))
                .withName("topology-config")
                .withNamespace("default")
                .endMetadata()
                .addToData("convert-target.yaml", target)
                .build();
        client.configMaps().inNamespace(namespace).createOrReplace(configMap);
    }
}
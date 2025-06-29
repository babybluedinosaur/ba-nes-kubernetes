package org.acme.TopologyReconciler.Utils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import java.io.IOException;
import java.util.Map;

public class ConfigBuilder {

    String namespace = "default";

    // Creates configmap, which contains converted yaml for nebuli input
    public void buildTargetMap(String fileName, io.fabric8.kubernetes.client.KubernetesClient client) throws IOException {
        TopologyConverter topologyConverter = new TopologyConverter(client);
        String convertedContent = null;
        if (fileName.startsWith("topology")) {
            convertedContent =
                    topologyConverter.convertTopology("src/main/resources/cr/topologies/" + fileName + ".yaml");
        } else {
            convertedContent =
                    topologyConverter.convertTopology("src/main/resources/cr/topologies/edgeless/" + fileName + ".yaml");
        }
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(Map.of("topology", "nes"))
                .withName("topology-config")
                .withNamespace(namespace)
                .endMetadata()
                .addToData("convert-target.yaml", convertedContent)
                .build();
        client.configMaps().inNamespace(namespace).createOrReplace(configMap);
    }
}
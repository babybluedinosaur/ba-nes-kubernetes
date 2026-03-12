package org.acme.TopologyReconciler.Utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.acme.TopologyReconciler.NesTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

// This class creates the initial topology configmap and the converted topology configmap, latter gets used as nebuli input
public class ConfigBuilder {

    private static final Logger log = LoggerFactory.getLogger(ConfigBuilder.class);
    String namespace = "default";
    Map<String, String> labels = Map.of("topology", "nes");
    String configMapNamePrefix = "topology-config-";
    String topologyFileName = "converted-topology.yaml";

    // Creates configmap, which contains initial topology
    public void buildTopologyMap(NesTopology cr, KubernetesClient client) throws IOException {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
        yamlMapper.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        String originalTopology = yamlMapper.writeValueAsString(cr.getSpec());

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(labels)
                .withName(configMapNamePrefix + cr.getMetadata().getName())
                .withNamespace(namespace)
                .endMetadata()
                .addToData(topologyFileName, originalTopology)
                .build();
        client.configMaps().inNamespace(namespace).createOrReplace(configMap);
    }

    // Converts existing topology config map
    public void convertTopologyMap(NesTopology cr, KubernetesClient client) throws IOException {
        // Get the original topology
        TopologyConverter topologyConverter = new TopologyConverter(client);
        ConfigMap topologyConfigMap = client.configMaps().inNamespace(namespace).withName(configMapNamePrefix +
                cr.getMetadata().getName()).get();

        // Convert the original topology
        String convertedTopologyContent = topologyConverter.
                convertTopology(topologyConfigMap.getData().get(topologyFileName));

        topologyConfigMap.getData().put(topologyFileName, convertedTopologyContent);
        client.configMaps().inNamespace(namespace).createOrReplace(topologyConfigMap);
    }
}
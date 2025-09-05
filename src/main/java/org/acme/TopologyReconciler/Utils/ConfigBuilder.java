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

// This class saves the topology yaml and the converted topology yaml, latter will get used as nebuli input
public class ConfigBuilder {

    private static final Logger log = LoggerFactory.getLogger(ConfigBuilder.class);
    String namespace = "default";
    Map<String, String> labels = Map.of("topology", "nes");
    String configMapName = "topology-config";
    String topologyFileName = "converted-topology.yaml";

    public void buildSourceMap(NesTopology cr, KubernetesClient client) throws IOException {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
        yamlMapper.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        String originalTopology = yamlMapper.writeValueAsString(cr);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withLabels(labels)
                .withName(configMapName)
                .withNamespace(namespace)
                .endMetadata()
                .addToData(topologyFileName, originalTopology)
                .build();
        client.configMaps().inNamespace(namespace).createOrReplace(configMap);
    }

    // Creates configmap, which contains converted yaml for nebuli input
    public void buildTargetMap(KubernetesClient client) throws IOException {
        // Get the original topology
        TopologyConverter topologyConverter = new TopologyConverter(client);
        ConfigMap topologyConfigMap = client.configMaps().inNamespace(namespace).withName(configMapName).get();

        // Convert the original topology
        String convertedTopologyContent = topologyConverter.
                convertTopology(topologyConfigMap.getData().get(topologyFileName));

        topologyConfigMap.getData().put(topologyFileName, convertedTopologyContent);
        client.configMaps().inNamespace(namespace).createOrReplace(topologyConfigMap);
    }
}
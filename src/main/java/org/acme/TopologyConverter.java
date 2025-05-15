package org.acme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.Service;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

// The function of this class is to convert the NES Kubernetes topology
// to a normal yaml, so that it can be used for NES nebuli
public class TopologyConverter {
    public TopologyConverter(String cr, io.fabric8.kubernetes.client.KubernetesClient client) throws IOException {
        // inital setup, get nodes section of custom resource
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
        JsonNode fullYaml = yamlMapper.readTree(new File(cr));
        JsonNode spec = fullYaml.get("spec");
        ArrayNode nodesArray = (ArrayNode) spec.get("nodes");

        for (int i = 0; i < nodesArray.size(); i++) {
            JsonNode nameNode = nodesArray.get(i).get("name");
            // create tmpNode for correct order of fields
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            ObjectNode tmpNode = mapper.createObjectNode();
            Service service = client.services().inNamespace("default").
                    withName(nameNode.asText() + "-service").get();
            String ip = service.getSpec().getClusterIP();
            tmpNode.put("connection", ip + ":" + "9090");
            tmpNode.put("grpc", ip + ":" + "8080");

            // delete useless fields in original node
            ObjectNode objectNode = (ObjectNode) nodesArray.get(i);
            objectNode.remove("name");
            objectNode.remove("image");

            // merge tmpNode and original node
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                tmpNode.put(key, value);
            }
            nodesArray.set(i, tmpNode);
        }

        File outputFile = new File("src/main/resources/crs/covert-target.yaml");
        yamlMapper.writeValue(outputFile, spec);
    }
}

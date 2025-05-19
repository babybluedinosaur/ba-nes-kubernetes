package org.acme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
        try {
            // inital setup, get nodes section in custom resource
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

                //add new fields
                Service service = client.services().inNamespace("default").
                        withName(nameNode.asText() + "-service").get();
                String name = service.getMetadata().getName();
                tmpNode.put("connection", name + ":" + "9090");
                tmpNode.put("grpc", name + ":" + "8080");

                // delete useless fields in original node
                ObjectNode originalNode = (ObjectNode) nodesArray.get(i);
                originalNode.remove("name");
                originalNode.remove("image");

                // adjust links for nebuli
                // TODO: upstream support
                if (nodesArray.get(i).has("links")) {
                    JsonNode downstream = nodesArray.get(i).get("links").get("downstreams");
                    if (downstream != null && downstream.isArray()) {
                        ArrayNode downstreamArray = (ArrayNode) downstream;
                        for (int j = 0; j < downstreamArray.size(); j++) {
                            String worker = downstreamArray.get(j).asText();
                            String workerService = worker + "-service" + ":" + "9090";
                            downstreamArray.set(j, new TextNode(workerService));
                        }
                    }

                }
                // merge tmp node and original node
                Iterator<Map.Entry<String, JsonNode>> fields = originalNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    tmpNode.put(field.getKey(), field.getValue());
                }
                nodesArray.set(i, tmpNode);
            }

            File outputFile = new File("src/main/resources/cr/convert-target.yaml");
            yamlMapper.writeValue(outputFile, spec);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

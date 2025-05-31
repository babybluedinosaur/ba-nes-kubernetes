package org.acme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationCollector;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

// The function of this class is to convert the NES Kubernetes topology
// to a normal yaml, so that it can be used for NES nebuli
public class TopologyConverter {

    io.fabric8.kubernetes.client.KubernetesClient client;

    public TopologyConverter(String cr, io.fabric8.kubernetes.client.KubernetesClient client) throws IOException {
        try {
            this.client = client;
            // inital setup, get nodes section in custom resource
            YAMLFactory yamlFactory = new YAMLFactory();
            yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
            ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
            JsonNode fullYaml = yamlMapper.readTree(new File(cr));
            JsonNode spec = fullYaml.get("spec");
            ArrayNode nodesArray = (ArrayNode) spec.get("nodes");

            for (int i = 0; i < nodesArray.size(); i++) {
                JsonNode node = nodesArray.get(i);
                JsonNode nameNode = node.get("name");

                // create tmpNode for correct order of fields
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                ObjectNode tmpNode = mapper.createObjectNode();

                //add new fields
                Service service = this.client.services().inNamespace("default").
                        withName(nameNode.asText() + "-service").get();
                String name = service.getMetadata().getName();
                tmpNode.put("connection", name + ":" + "9090");
                tmpNode.put("grpc", name + ":" + "8080");

                // delete useless fields in original node
                ObjectNode originalNode = (ObjectNode) node;
                originalNode.remove("name");
                originalNode.remove("image");

                // adjust links for nebuli
                // TODO: upstream support
                if (node.has("links")) {
                    JsonNode downstream = node.get("links").get("downstreams");
                    if (downstream != null && downstream.isArray()) {
                        ArrayNode downstreamArray = (ArrayNode) downstream;
                        for (int j = 0; j < downstreamArray.size(); j++) {
                            String worker = downstreamArray.get(j).asText();
                            String workerService = worker + "-service" + ":" + "9090";
                            downstreamArray.set(j, new TextNode(workerService));
                        }
                    }

                }

                //TODO: neue function wo wir services auch für die sources erstellen
                if (node.has("physical")) {
                    ArrayNode physicalArray = (ArrayNode) node.get("physical");
                    for (int j = 0; j < physicalArray.size(); j++) {
                        JsonNode physicalNode = physicalArray.get(j);
                        createPhysicalService(physicalNode);
                        //TODO: an socketHost "-service" ranhängen
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createPhysicalService(JsonNode physicalNode) throws Exception {
        if (physicalNode.has("sourceConfig")) {
            JsonNode sourceConfig = physicalNode.get("sourceConfig");
            if (sourceConfig.has("socketHost") && sourceConfig.has("socketPort") &&
                    sourceConfig.has("type")) {
                createService(
                        sourceConfig.get("socketHost").asText(),
                        sourceConfig.get("socketPort").asInt(),
                        sourceConfig.get("type").asText()
                );
                String socketHost = sourceConfig.get("socketHost").asText();
                String serviceName = socketHost + "-service";
                ((ObjectNode) sourceConfig).set("socketHost", new TextNode(serviceName));
            } else {
                throw new Exception("sourceConfig is incomplete. Please add socketHost and socketPort.");
            }
        } else {
            throw new Exception("Invalid physical source. Please add a sourceConfig.");
        }
    }

    private void createService(String socketHost, int socketPort, String protocol) throws Exception {
        Service service = new ServiceBuilder()
                .withNewMetadata().withName(socketHost + "-service").withLabels(Map.of("topology", "nes"))
                .endMetadata()
                .withNewSpec()
                .addToSelector("app", socketHost)
                .addNewPort()
                .withPort(socketPort)
                .withNewTargetPort(9999)
                .withProtocol(protocol)
                .endPort()
                .withType("ClusterIP") // we only communicate inside cluster
                .endSpec()
                .build();

        try {
            this.client.services().inNamespace("default").createOrReplace(service);
        } catch (Exception e) {
            System.out.println("error creating service: " + e.getMessage());
        }
    }
}

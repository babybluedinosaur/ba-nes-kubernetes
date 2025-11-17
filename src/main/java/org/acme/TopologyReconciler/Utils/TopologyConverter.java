package org.acme.TopologyReconciler.Utils;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The function of this class is to convert the NES Kubernetes topology
// to a normal yaml, so that it can be used for NES nebuli
public class TopologyConverter {

    final static Logger logger = LogManager.getLogger(TopologyConverter.class);
    io.fabric8.kubernetes.client.KubernetesClient client;
    String namespace = "default";
    final static String serviceSuffix = "-service";
    final static String hostPortSuffix = ":9090";
    final static String grpcPortSuffix = ":8080";

    public TopologyConverter(io.fabric8.kubernetes.client.KubernetesClient client) {
        this.client = client;
    }

    public String convertTopology(String cr) throws IOException {
        try {
            // inital setup, get nodes section in custom resource
            YAMLFactory yamlFactory = new YAMLFactory();
            yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
            ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
            JsonNode fullYaml = yamlMapper.readTree(cr);
            JsonNode spec = fullYaml.get("spec");
            if (spec == null) {
                return "{}";
            }
            ArrayNode sinksArray = getSinks(spec);
            ArrayNode physicalArray = getPhysicalSources(spec);
            ArrayNode nodesArray = getWorkerNodes(spec);

            if (sinksArray != null) convertSinks(sinksArray);
            if (physicalArray != null) convertPhysicalSources(physicalArray);
            if (nodesArray != null) convertWorkerNodes(nodesArray);

            return yamlMapper.writeValueAsString(spec);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "";
    }

    private ArrayNode getSinks(JsonNode spec) {
        JsonNode sinksNode = spec.get("sinks");
        if (sinksNode == null) {
            logger.warn("sinks is null");
            return null;
        }
        if (!sinksNode.isArray()) {
            logger.warn("sinks exists but is not an array, type: {}", sinksNode.getNodeType());
            return null;
        }
        return (ArrayNode) sinksNode;
    }

    private ArrayNode getPhysicalSources(JsonNode spec) {
        JsonNode physicalNode = spec.get("physicalSources");
        if (physicalNode == null) {
            logger.warn("physicalSources is null");
            return null;
        }

        if (!physicalNode.isArray()) {
            logger.warn("physicalSources exists but is not an array, type: {}", physicalNode.getNodeType());
            return null;
        }
        return (ArrayNode) spec.get("physicalSources");
    }

    private ArrayNode getWorkerNodes(JsonNode spec) {
        JsonNode workerNode = spec.get("workerNodes");
        if (workerNode == null) {
            logger.warn("workerNodes is null");
            return null;
        }

        if (!workerNode.isArray()) {
            logger.warn("workerNodes exists but is not an array, type: {}", workerNode.getNodeType());
            return null;
        }

        return (ArrayNode) spec.get("workerNodes");
    }

    private void convertSinks(ArrayNode sinksArray) throws IOException {
        for (int i = 0; i < sinksArray.size(); i++) {
            JsonNode sink = sinksArray.get(i);
            String host = sink.get("host").asText();
            if (host != null && !host.isEmpty()) {
                ((ObjectNode) sink).put("host", host + "-service:9090");
            } else {
                logger.error("sink host is null or empty");
            }
        }
    }

    private void convertPhysicalSources(ArrayNode physicalArray) throws Exception {
        for (int i = 0; i < physicalArray.size(); i++) {
            JsonNode physicalNode = physicalArray.get(i);
            String host = physicalNode.get("host").asText() + serviceSuffix + hostPortSuffix;
            ((ObjectNode) physicalNode).put("host", host);
            createPhysicalService(physicalNode);
        }
    }

    private void convertWorkerNodes(ArrayNode nodesArray) throws IOException {
        for (int i = 0; i < nodesArray.size(); i++) {
            JsonNode node = nodesArray.get(i);
            JsonNode nameNode = node.get("host");

            // create tmpNode for correct order of fields
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            ObjectNode tmpNode = mapper.createObjectNode();

            //add new fields
            String name = nameNode.asText() + serviceSuffix;
            tmpNode.put("host", name + hostPortSuffix);
            tmpNode.put("grpc", name + grpcPortSuffix);

            // delete useless fields in original node
            ObjectNode originalNode = (ObjectNode) node;
            originalNode.remove("host");
            originalNode.remove("image");

            // adjust links for nebuli
            JsonNode downstream = node.get("downstreamNodes");
            if (downstream != null && downstream.isArray()) {
                ArrayNode downstreamArray = (ArrayNode) downstream;
                for (int j = 0; j < downstreamArray.size(); j++) {
                    String worker = downstreamArray.get(j).asText();
                    String workerService = worker + serviceSuffix + hostPortSuffix;
                    downstreamArray.set(j, new TextNode(workerService));
                }
            }

            JsonNode upstream = node.get("upstreamNodes");
            if (upstream != null && upstream.isArray()) {
                ArrayNode upstreamArray = (ArrayNode) upstream;
                for (int j = 0; j < upstreamArray.size(); j++) {
                    String worker = upstreamArray.get(j).asText();
                    String workerService = worker + serviceSuffix + hostPortSuffix;
                    upstreamArray.set(j, new TextNode(workerService));
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
    }

    // Creates Service for a phyiscal source
    private void createPhysicalService(JsonNode physicalNode) throws Exception {
        if (physicalNode.has("sourceConfig")) {
            JsonNode sourceConfig = physicalNode.get("sourceConfig");
            if (sourceConfig.has("socketHost") && sourceConfig.has("socketPort") &&
                    physicalNode.has("type")) {
                createService(
                        sourceConfig.get("socketHost").asText(),
                        sourceConfig.get("socketPort").asInt(),
                        physicalNode.get("type").asText()
                );
                String socketHost = sourceConfig.get("socketHost").asText();
                String serviceName = socketHost + "-service";
                ((ObjectNode) sourceConfig).set("socketHost", new TextNode(serviceName));
            } else if (!sourceConfig.has("filePath")) {
                throw new Exception("sourceConfig is incomplete. Please add socketHost and socketPort. Is 'type'" +
                        " missing?");
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
            this.client.services().inNamespace(namespace).createOrReplace(service);
        } catch (Exception e) {
            logger.error("error creating service: {}", e.getMessage());
        }
    }
}

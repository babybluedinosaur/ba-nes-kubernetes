package org.acme.QueryReconciler.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlQueryModifier {

    public static String appendToSinkArgs(String query, String targetName, String paramsToAdd) {
        // Match INTO <targetName> ( with any whitespace in between
        Pattern pattern = Pattern.compile(
                "(?i)INTO\\s+" + Pattern.quote(targetName) + "\\s*\\("
        );
        Matcher matcher = pattern.matcher(query);

        if (!matcher.find()) return query;

        int openParen = matcher.end() - 1; // index of '('
        int closeParen = findMatchingCloseParen(query, openParen);

        if (closeParen == -1) return query;

        String existing = query.substring(openParen + 1, closeParen).trim();
        String combined = existing.isEmpty() ? paramsToAdd : existing + ", " + paramsToAdd;

        return query.substring(0, openParen + 1)
                + combined
                + query.substring(closeParen);
    }

    private static int findMatchingCloseParen(String query, int openParenIdx) {
        int depth = 1;
        for (int i = openParenIdx + 1; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    public static void insertQueryIntoTopologyMap(KubernetesClient client, String topologyName, String topologyFileName,
                                            String configMapNamePrefix, String query) throws JsonProcessingException {
        ConfigMap topologyConfigMap = client.configMaps()
                .inNamespace(client.getNamespace())
                .withName(configMapNamePrefix + topologyName)
                .get();

        if (topologyConfigMap == null) return;

        String originalTopology = topologyConfigMap.getData().get(topologyFileName);
        if (originalTopology == null) return;

        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);

        ObjectNode originalNode = (ObjectNode) yamlMapper.readTree(originalTopology);

        // Extract host from first worker
        String host = null;
        if (originalNode.has("workers") && originalNode.get("workers").isArray()) {
            JsonNode firstWorker = originalNode.get("workers").get(0);
            if (firstWorker != null && firstWorker.has("host")) {
                host = firstWorker.get("host").asText();
            }
        }

        String modifiedQuery = query;

        // Inject host into source function
        if (host != null && !host.isEmpty() && query.contains("(") && query.contains("INTO")) {
            modifiedQuery = modifiedQuery.replaceFirst(
                    "\\)\\s*\n\\s*INTO",
                    ",'" + host + "' AS `SOURCE`.`HOST`)\nINTO"
            );
        }

        // Inject sink parameters
        if (originalNode.has("sinks") && originalNode.get("sinks").isArray()) {
            JsonNode sinkNode = originalNode.get("sinks").get(0);
            if (sinkNode == null) {
                return;
            }

            String sinkName = sinkNode.has("name") ? sinkNode.get("name").asText() : "File";
            JsonNode sinkConfig = sinkNode.get("config");

            if (sinkConfig == null) {
                return;
            }

            String outputFormat = sinkConfig.has("output_format")
                    ? sinkConfig.get("output_format").asText()
                    : "CSV";

            String filePath = sinkConfig.has("file_path")
                    ? sinkConfig.get("file_path").asText()
                    : "/sink-output/sink-output.csv";

            if (host != null && !host.isEmpty()) {
                String sinkParams = "'" + host + "' AS `SINK`.`HOST`,\n"
                        + "'" + filePath + "' AS `SINK`.FILE_PATH,\n"
                        + "'" + outputFormat + "' AS `SINK`.OUTPUT_FORMAT";
                modifiedQuery = SqlQueryModifier.appendToSinkArgs(modifiedQuery, sinkName, sinkParams);
            }
        }

        ObjectNode tmpNode = yamlMapper.createObjectNode();
        tmpNode.put("query", modifiedQuery);
        tmpNode.setAll(originalNode);

        String updatedTopology = yamlMapper.writeValueAsString(tmpNode);
        topologyConfigMap.getData().put(topologyFileName, updatedTopology);
        client.configMaps()
                .inNamespace(client.getNamespace())
                .withName(configMapNamePrefix + topologyName)
                .createOrReplace(topologyConfigMap);

    }
}
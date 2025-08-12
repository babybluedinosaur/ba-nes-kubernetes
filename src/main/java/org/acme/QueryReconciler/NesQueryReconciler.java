package org.acme.QueryReconciler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.acme.QueryReconciler.Nebuli.Nebuli;
import org.acme.QueryReconciler.Utils.TopologyMounter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

public class NesQueryReconciler implements Reconciler<NesQuery> {

    private static final Logger logger = LogManager.getLogger(NesQueryReconciler.class);
    private String namespace;
    io.fabric8.kubernetes.client.KubernetesClient client;

    public NesQueryReconciler(KubernetesClient client) {
        this.client = client;
        this.namespace = client.getNamespace();
    }

    public UpdateControl<NesQuery> reconcile(NesQuery desired, Context<NesQuery> context) throws Exception {
        logger.info("starting reconcile");

        NesQueryStatus status = desired.getStatus();
        String deploymentName = "query-" + desired.getMetadata().getName();
        // If we do not use this, the first query will always be ignored
        boolean statusWasNull = false;

        // Create status for query, important to stop later the query (see stopNebuli())
        if (status == null) {
            status = new NesQueryStatus();
            status.setDeploymentName(deploymentName);
            desired.setStatus(status);
            logger.info("setting initial status for query: {}", desired.getMetadata().getName());
            statusWasNull = true;
        }

        NesQuerySpec spec = desired.getSpec();
        Nebuli nebuli = spec.getNebuli();
        String query = spec.getQuery();
        String name = desired.getMetadata().getName();
        String arg = nebuli.getArgs();
        insertQueryIntoConfigMap(query);

        Job leftoverNebuli = client.batch().v1().jobs().inNamespace(namespace).withName(name).get();
        if (leftoverNebuli != null) {
            client.batch().v1().jobs().inNamespace(namespace).withName(name).delete();
        }

        logger.info("argument received in reconcile: '{}'", arg);
        if (arg.equals("start")) {
            Job job = buildNebuli(desired, nebuli, query);
            try {
                client.batch().v1().jobs().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(job);
            } catch (Exception e) {
                logger.error("error creating deployment: " + e.getMessage());
            }
        } else if (arg.equals("stop")) {
            stopNebuli(desired);
        } else {
            logger.error("unsupported nebuli argument.");
        }

        if (statusWasNull) {
            return UpdateControl.patchStatus(desired);
        }

        return UpdateControl.noUpdate();
    }

    // This method inserts the query into the existing topology yaml file
    private void insertQueryIntoConfigMap(String query) throws JsonProcessingException {
        ConfigMap topologyConfigMap = client.configMaps()
                .inNamespace(client.getNamespace())
                .withName("topology-config")
                .get();

        if (topologyConfigMap != null) {
            String originalTopology = topologyConfigMap.getData().get("convert-target.yaml");
            if (originalTopology != null) {
                YAMLFactory yamlFactory = new YAMLFactory();
                yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
                ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);

                ObjectNode originalNode = (ObjectNode) yamlMapper.readTree(originalTopology);
                ObjectNode tmpNode = yamlMapper.createObjectNode();
                tmpNode.put("query", query);
                tmpNode.setAll(originalNode);

                String updatedTopology = yamlMapper.writeValueAsString(tmpNode);
                topologyConfigMap.getData().put("convert-target.yaml", updatedTopology);
                client.configMaps()
                        .inNamespace(client.getNamespace())
                        .withName("topology-config")
                        .createOrReplace(topologyConfigMap);
            } else {
                logger.error("convert-target.yaml not found in configmap topology-config");
            }
        } else {
            logger.error("configmap topology-config not found");
        }
    }

    private Job buildNebuli(NesQuery desired, Nebuli nebuli, String query) throws Exception {
        Container nebuliContainer = buildNebuliContainer(nebuli, query);
        return buildJob(desired, nebuliContainer);
    }

    private Container buildNebuliContainer(Nebuli nebuli, String query) throws IOException {
        Container nebuliContainer = new ContainerBuilder()
                .withName("nebuli")
                .withImage(nebuli.getImage())
                .withImagePullPolicy("IfNotPresent")
                .withArgs("/topology/convert-target.yaml")
                .withVolumeMounts(
                        TopologyMounter.buildTopologyMap(client)
                )
                .build();

        return nebuliContainer;
    }

    private Job buildJob(NesQuery desired, Container container) {
        String name = container.getName();
        String jobName = "query-" + desired.getMetadata().getName();
        Job job = new JobBuilder()
                .withNewMetadata().withName(jobName).withLabels(Map.of("query", "nebuli")).endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewMetadata().addToLabels("query", name).endMetadata()
                .withNewSpec()
                .addToContainers(container)
                .withVolumes(TopologyMounter.createVolume())
                .withRestartPolicy("OnFailure")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        return job;
    }

    private void stopNebuli(NesQuery desired) throws Exception {
        NesQueryStatus status = desired.getStatus();
        String deploymentName = status.getDeploymentName();
        logger.info("stopping nebuli {}", deploymentName);
        client.apps().deployments()
                .withName(deploymentName)
                .delete();
    }

}

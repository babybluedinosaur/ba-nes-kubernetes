package org.acme.QueryReconciler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.*;
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
    private static final String topologyFileName = "converted-topology.yaml";
    private static String configMapNamePrefix = "topology-config-";
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
            status.setPhase("Pending");
            desired.setStatus(status);
            logger.info("setting initial status for query: {}", desired.getMetadata().getName());
            statusWasNull = true;
        }

        NesQuerySpec spec = desired.getSpec();
        Nebuli nebuli = spec.getNebuli();
        String query = spec.getQuery();
        String topologyName = spec.getTopologyName();
        String name = desired.getMetadata().getName();
        String arg = nebuli.getArgs();

        Job leftoverNebuli = client.batch().v1().jobs().inNamespace(namespace).withName(name).get();

        logger.info("argument received in reconcile: '{}'", arg);
        if (arg.equals("start")) {
            if (leftoverNebuli == null) {
                insertQueryIntoTopologyMap(topologyName, query);
                Job job = buildNebuli(desired, nebuli);
                try {
                    client.batch().v1().jobs().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(job);
                    status.setPhase("Running");
                } catch (Exception e) {
                    logger.error("error creating deployment: {}", e.getMessage());
                    status.setPhase("Failed");
                }
            }
            else {
                Integer succeeded = leftoverNebuli.getStatus().getSucceeded();
                Integer failed = leftoverNebuli.getStatus().getFailed();
                if (succeeded != null && succeeded > 0) {
                    status.setPhase("Completed");
                } else if (failed != null && failed > 0) {
                    status.setPhase("Failed");
                } else {
                    status.setPhase("Running");
                }
            }
        } else if (arg.equals("stop")) {
            stopNebuli(desired);
        } else {
            logger.error("unsupported nebuli argument.");
        }

        if (statusWasNull) {
            return UpdateControl.patchStatus(desired);
        }

        desired.setStatus(status);
        return UpdateControl.patchStatus(desired).rescheduleAfter(5000);
    }

    // This method inserts the query into the existing topology yaml file
    private void insertQueryIntoTopologyMap(String topologyName, String query) throws JsonProcessingException {
        ConfigMap topologyConfigMap = client.configMaps()
                .inNamespace(client.getNamespace())
                .withName(configMapNamePrefix + topologyName)
                .get();

        if (topologyConfigMap != null) {
            String originalTopology = topologyConfigMap.getData().get(topologyFileName);
            if (originalTopology != null) {
                YAMLFactory yamlFactory = new YAMLFactory();
                yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
                ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);

                ObjectNode originalNode = (ObjectNode) yamlMapper.readTree(originalTopology);
                ObjectNode tmpNode = yamlMapper.createObjectNode();
                tmpNode.put("query", query.toUpperCase());
                tmpNode.setAll(originalNode);

                String updatedTopology = yamlMapper.writeValueAsString(tmpNode);
                topologyConfigMap.getData().put(topologyFileName, updatedTopology);
                client.configMaps()
                        .inNamespace(client.getNamespace())
                        .withName(configMapNamePrefix + topologyName)
                        .createOrReplace(topologyConfigMap);
                logger.info("updated topology config map");
            } else {
                logger.error(topologyFileName + " not found in configmap " + configMapNamePrefix + topologyName);
            }
        } else {
            logger.error("configmap {} not found", configMapNamePrefix + topologyName);
        }
    }

    private Job buildNebuli(NesQuery desired, Nebuli nebuli) throws Exception {
        Container nebuliContainer = buildNebuliContainer(nebuli);
        return buildJob(desired, nebuliContainer);
    }

    private Container buildNebuliContainer(Nebuli nebuli) throws IOException {
        Container nebuliContainer = new ContainerBuilder()
                .withName("nebuli")
                .withImage(nebuli.getImage())
                .withImagePullPolicy("IfNotPresent")
                .withArgs("/topology/" + topologyFileName)
                .withVolumeMounts(
                        TopologyMounter.buildTopologyMount()
                )
                .build();

        return nebuliContainer;
    }

    private Job buildJob(NesQuery desired, Container container) {
        String name = container.getName();
        String jobName = desired.getMetadata().getName();
        var crMeta = desired.getMetadata();

        Job job = new JobBuilder()
                .withNewMetadata()
                    .withName(jobName)
                    .withLabels(Map.of("query", "nebuli"))
                    .addNewOwnerReference()
                        .withApiVersion(desired.getApiVersion())
                        .withKind(desired.getKind())
                        .withName(crMeta.getName())
                        .withUid(crMeta.getUid())
                        .withController(true)
                        .withBlockOwnerDeletion(true)
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec()
                    .withNewTemplate()
                        .withNewMetadata().addToLabels("query", name).endMetadata()
                        .withNewSpec()
                            .addToContainers(container)
                            .withTerminationGracePeriodSeconds(3L)
                            .withVolumes(TopologyMounter.createVolume(configMapNamePrefix + desired.getSpec().getTopologyName()))
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
        client.batch().v1().jobs()
                .withName(deploymentName)
                .delete();
    }

}

package org.acme.QueryReconciler;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
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
    io.fabric8.kubernetes.client.KubernetesClient client;

    public NesQueryReconciler(KubernetesClient client) {
        this.client = client;
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
        String query = spec.getQuery();
        Nebuli nebuli = spec.getNebuli();
        String arg = nebuli.getArgs();

        //TODO: maybe replace sleep with actual status check (is topology ready?)
        logger.info("argument received in reconcile: '{}'", arg);
        if (arg.equals("start")) {
            Deployment deployment = buildNebuli(desired, nebuli, query);
            Thread.sleep(1000);
            try {
                client.apps().deployments().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(deployment);
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

    private Deployment buildNebuli(NesQuery desired, Nebuli nebuli, String query) throws Exception {
        Container nebuliContainer = buildNebuliContainer(nebuli, query);
        return buildDeployment(desired, nebuliContainer);
    }

    private Container buildNebuliContainer(Nebuli nebuli, String query) throws IOException {
        Container nebuliContainer = new ContainerBuilder()
                .withName("nebuli")
                .withImage(nebuli.getImage())
                .withImagePullPolicy("Always")
                .withArgs("/topology/convert-target.yaml", query)
                .withVolumeMounts(
                        TopologyMounter.buildTopologyMap(client)
                )
                .build();

        return nebuliContainer;
    }

    private Deployment buildDeployment(NesQuery desired, Container container) {
        String name = container.getName();
        String deploymentName = "query-" + desired.getMetadata().getName();
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName(deploymentName).withLabels(Map.of("query", "nebuli")).endMetadata()
                .withNewSpec()
                .withNewSelector().addToMatchLabels("query", name).endSelector()
                .withNewTemplate()
                .withNewMetadata().addToLabels("query", name).endMetadata()
                .withNewSpec()
                .addToContainers(container)
                .withVolumes(TopologyMounter.createVolume())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        return deployment;
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

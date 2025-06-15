package org.acme.QueryReconciler;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.SleepAction;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.acme.QueryReconciler.Nebuli.Nebuli;
import org.acme.QueryReconciler.Utils.IdCounter;
import org.acme.QueryReconciler.Utils.TopologyMounter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;

public class NesQueryReconciler implements Reconciler<NesQuery> {

    private static final Logger logger = LogManager.getLogger(NesQueryReconciler.class);
    io.fabric8.kubernetes.client.KubernetesClient client;
    NesQueryStatus status;
    ConfigMap idCounter;
    String namespace = "default";

    public NesQueryReconciler(KubernetesClient client) {
        this.client = client;
        this.status = new NesQueryStatus();
        this.idCounter = createCounter();
    }

    public UpdateControl<NesQuery> reconcile(NesQuery desired, Context<NesQuery> context) throws Exception {
        String query = desired.getSpec().getQuery();
        Nebuli nebuli = desired.getSpec().getNebuli();
        String arg = nebuli.getArgs();

        //TODO: maybe replace sleep with actual status check (is topology ready?)
        if (arg.equals("start")) {
            Deployment deployment = createNebuli(nebuli, query);
            Thread.sleep(1000);
            try {
                client.apps().deployments().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(deployment);
            } catch (Exception e) {
                logger.error("error creating deployment: " + e.getMessage());
            }
        } else if (arg.equals("stop")) {
            stopNebuli(desired);
        } else {
            logger.error("Unsupported nebuli argument.");
        }
        return UpdateControl.noUpdate();
    }

    private Deployment createNebuli(Nebuli nebuli, String query) throws Exception {
        Container nebuliContainer = createNebuliContainer(nebuli, query);
        return createDeployment(nebuli, nebuliContainer);
    }

    private Container createNebuliContainer(Nebuli nebuli, String query) throws IOException {
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

    private Deployment createDeployment(Nebuli nebuli, Container container) {
        String name = container.getName();
        String deploymentName = updateCounter();
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

        status.setDeploymentName(deploymentName);
        return deployment;
    }

    private void stopNebuli(NesQuery desired) throws Exception {
        // delete deployment
        logger.info("stopping nebuli {}", status.getDeploymentName());
        client.apps().deployments()
                .withName(status.getDeploymentName())
                .delete();
    }

    private ConfigMap createCounter() {
        ConfigMap idCounter = client.configMaps().inNamespace(namespace)
                .withName("query-id")
                .get();
        if (idCounter == null) {
            idCounter = new IdCounter().createCounter(client);
        }
        return idCounter;
    }

    private String updateCounter() {
        Map<String,String> counterMap = idCounter.getData();
        int newCounter = Integer.valueOf(counterMap.get("counter"));
        newCounter++;

        String stringCounter = String.valueOf(newCounter);
        counterMap.put("counter", stringCounter);
        String deploymentName = "query-" + stringCounter;

        spawnConfig();

        return deploymentName;
    }

    private void resetCounter() {
        if (idCounter == null) {
            logger.warn("there is no query-id config to clean");
        } else {
            Map<String,String> counterMap = idCounter.getData();

            int reset = 0;
            String stringCounter = String.valueOf(reset);
            counterMap.put("counter", stringCounter);

            spawnConfig();
        }
    }

    private void spawnConfig() {
        try {
            client.configMaps().inNamespace(namespace).createOrReplace(idCounter);
        } catch (Exception e) {
            logger.error("error creating/updating configmap: " + e.getMessage());
        }
    }

}

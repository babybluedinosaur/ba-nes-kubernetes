package org.acme.TopologyReconciler;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.acme.TopologyReconciler.Utils.ConfigBuilder;
import org.acme.TopologyReconciler.Worker.NesWorker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.util.*;

public class NesTopologyReconciler implements Reconciler<NesTopology> {

    private static final Logger logger = LogManager.getLogger(NesTopologyReconciler.class);
    io.fabric8.kubernetes.client.KubernetesClient client;
    Map<String, NesWorker> workerMap;

    public NesTopologyReconciler(io.fabric8.kubernetes.client.KubernetesClient client) {
        this.client = client;
        this.workerMap = new HashMap<>(); // Contains latest workerSpecs
    }

    // Create a deployment and service for each worker
    public UpdateControl<NesTopology> reconcile(NesTopology desired, Context<NesTopology> context) throws IOException {
        logger.info("Starting reconcile");
        for (Container container : createContainers(desired)) {
            createService(desired, container);
            createDeployment(desired, container);
        }
        ConfigBuilder configBuilder = new ConfigBuilder();
        configBuilder.buildTargetMap(
                desired.getMetadata().getAnnotations().get("topology-file-name"),
                client
        );
        cleanup(desired);
        return UpdateControl.noUpdate();
    }

    public List<Container> createContainers(NesTopology desired) {
        List<Container> containers = new ArrayList<>();
        for (NesWorker worker : desired.getSpec().getNodes()) {
            String name = worker.getName();
            workerMap.put(name, worker); // Duplicates get overwritten
            Container container = new Container();
            container.setName(name);
            container.setImage(worker.getImage());
            container.setImagePullPolicy("Always");
            container.setArgs(Arrays.asList(
                    worker.getBind(),
                    worker.getConnection() + name + "-service:9090"));
            container.setPorts(Arrays.asList(
                    new ContainerPortBuilder().withContainerPort(8080).build(),
                    new ContainerPortBuilder().withContainerPort(9090).build()
            ));
            containers.add(container);
        }
        return containers;
    }

    public void createDeployment(NesTopology desired, Container container) {
        String name = container.getName();
        Map<String, String> labels = new HashMap<>();
        labels.put("app", name);
        labels.put("nes", "worker");
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName(name).withLabels(Map.of("nes", "worker")).endMetadata()
                .withNewSpec()
                .withNewSelector().addToMatchLabels(labels).endSelector()
                .withNewTemplate()
                .withNewMetadata().addToLabels(labels).endMetadata()
                .withNewSpec()
                .addToContainers(container)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        try {
            client.apps().deployments().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(deployment);
        } catch (Exception e) {
            logger.error("error creating deployment {}: {}", name, e.getMessage());
        }
    }

    public void createService(NesTopology desired, Container container) {
        String name = container.getName();
        Service service = new ServiceBuilder()
                .withNewMetadata().withName(name + "-service").withLabels(Map.of("topology", "nes")).endMetadata()
                .withNewSpec()
                .addToSelector("app", name)
                .addNewPort()
                .withName("grpc")
                .withPort(8080)
                .withNewTargetPort(8080)
                .endPort()
                .addNewPort()
                .withName("connection")
                .withPort(9090)
                .withNewTargetPort(9090)
                .endPort()
                .withType("ClusterIP") // we only communicate inside cluster
                .endSpec()
                .build();

        try {
            client.services().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(service);
        } catch (Exception e) {
            logger.error("error creating service {}: {}", name, e.getMessage());
        }
    }

    // Delete obsolete deployments and services by comparing current state with the desired state
    public void cleanup(NesTopology desired) {
        List<Deployment> currentDeployments = client.apps().deployments()
                .inNamespace(desired.getMetadata().getNamespace())
                .withLabel("topology", "nes")
                .list()
                .getItems();

        Set<String> desiredNames = new HashSet<>();
        for (NesWorker worker : desired.getSpec().getNodes()) {
            desiredNames.add(worker.getName());
        }

        for (Deployment deployment : currentDeployments) {
            String deploymentName = deployment.getMetadata().getName();
            if (!desiredNames.contains(deploymentName) && !deploymentName.startsWith("tcp-server")
            && !deploymentName.startsWith("query")) {
                System.out.println("deleting deployment...: " + deploymentName);
                client.apps().deployments()
                        .inNamespace(desired.getMetadata().getNamespace())
                        .withName(deployment.getMetadata().getName())
                        .delete();
                System.out.println("deployment deleted successfully");
                System.out.println("deleting service...: " + deployment.getMetadata().getName() + "-service");
                client.services()
                        .inNamespace(deployment.getMetadata().getNamespace())
                        .withName(deploymentName + "-service")
                        .delete();
                System.out.println("service deleted successfully");
            }
        }
    }
}

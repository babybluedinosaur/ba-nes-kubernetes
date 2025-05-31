package org.acme;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.acme.setup.PVCBuilder;
import org.acme.setup.ReaderJobBuilder;
import org.acme.worker.NesWorker;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class NesTopologyReconciler implements Reconciler<NesTopology> {

    io.fabric8.kubernetes.client.KubernetesClient client;
    Map<String, NesWorker> workerMap;

    public NesTopologyReconciler(io.fabric8.kubernetes.client.KubernetesClient client) {
        this.client = client;
        this.workerMap = new HashMap<>(); // Contains latest workerSpecs
    }

    // Create a deployment and service for each worker
    public UpdateControl<NesTopology> reconcile(NesTopology desired, Context<NesTopology> context) throws IOException {
        PVCBuilder.spawnPVC(1, client);
        ReaderJobBuilder jobBuilder = new ReaderJobBuilder(client);
        for (Container container : createContainers(desired)) {
            createService(desired, container);
            createDeployment(desired, container);
        }
        TopologyConverter topologyConverter = new TopologyConverter("src/main/resources/cr/convert-source.yaml", client);
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
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec()
                .withNewSelector().addToMatchLabels("app", name).endSelector()
                .withNewTemplate()
                .withNewMetadata().addToLabels("app", name).endMetadata()
                .withNewSpec()
                .addToContainers(container)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        try {
            client.apps().deployments().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(deployment);
        } catch (Exception e) {
            System.out.println("error creating deployment: " + e.getMessage());
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
            System.out.println("error creating service: " + e.getMessage());
        }
    }

    // Delete obsolete deployments and services by comparing current state with the desired state
    public void cleanup(NesTopology desired) {
        List<Deployment> currentDeployments = client.apps().deployments()
                .inNamespace(desired.getMetadata().getNamespace())
                .list()
                .getItems();
        Set<String> desiredNames = desired.getSpec().getNodes()
                .stream()
                .map(NesWorker::getName)
                .collect(Collectors.toSet());

        for (Deployment deployment : currentDeployments) {
            String deploymentName = deployment.getMetadata().getName();
            if (!desiredNames.contains(deploymentName) && !deploymentName.startsWith("tcp-server")
                    && !deploymentName.startsWith("nebuli-queries-reader")) {
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

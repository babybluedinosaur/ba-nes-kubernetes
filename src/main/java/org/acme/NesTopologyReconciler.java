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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NesTopologyReconciler implements Reconciler<NesTopology> {

    io.fabric8.kubernetes.client.KubernetesClient client;

    public NesTopologyReconciler(io.fabric8.kubernetes.client.KubernetesClient client) {
        this.client = client;
    }

    public UpdateControl<NesTopology> reconcile(NesTopology desired, Context<NesTopology> context) {
        // Create a deployment and service for each worker
        for (Container container : createContainers(desired)) {
            createDeployment(desired, container);
            createService(desired, container);
        }
        cleanup(desired);
        return UpdateControl.noUpdate();
    }

    public List<Container> createContainers(NesTopology desired) {
        List<Container> containers = new ArrayList<>();
        for (WorkerSpec workerSpec : desired.getSpec().getWorkerSpecs()) {
            Container container = new Container();
            container.setName(workerSpec.getName());
            container.setImage(workerSpec.getImage());
            container.setArgs(workerSpec.getArgs());
            container.setPorts(Arrays.asList(new ContainerPortBuilder().withContainerPort(8080).build())); // for experiment
            containers.add(container);
            System.out.println(" - " + container.getName() + " : " + container.getImage());
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

        System.out.println("creating deployment...: " + deployment.getMetadata().getName());
        try {
            client.apps().deployments().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(deployment);
        } catch (Exception e) {
            System.out.println("error creating deployment: " + e.getMessage());
        }
        System.out.println("deployment created successfully");
    }

    public void createService(NesTopology desired, Container container) {
        Service service = new ServiceBuilder()
                .withNewMetadata().withName(container.getName() + "-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", container.getName())
                .addNewPort()
                .withPort(8080) // testing for experiment
                .withNewTargetPort(8080)
                .endPort()
                .withType("ClusterIP") // we only communicate inside cluster
                .endSpec()
                .build();

        System.out.println("creating service...: " + service.getMetadata().getName());
        try {
            client.services().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(service);
        } catch (Exception e) {
            System.out.println("error creating service: " + e.getMessage());
        }
        System.out.println("service created successfully");
    }

    //Delete obsolete deployments and services by comparing current state with the desired state
    public void cleanup(NesTopology desired) {
        List<Deployment> currentDeployments = client.apps().deployments()
                .inNamespace(desired.getMetadata().getNamespace())
                .list()
                .getItems();
        Set<String> desiredNames = desired.getSpec().getWorkerSpecs()
                .stream()
                .map(WorkerSpec::getName)
                .collect(Collectors.toSet());

        for (Deployment deployment : currentDeployments) {
            if(!desiredNames.contains(deployment.getMetadata().getName())) {
                System.out.println("deleting deployment...: " + deployment.getMetadata().getName());
                client.apps().deployments()
                        .inNamespace(desired.getMetadata().getNamespace())
                        .withName(deployment.getMetadata().getName())
                        .delete();
                System.out.println("deployment deleted successfully");
                System.out.println("deleting service...: " + deployment.getMetadata().getName() + "-service");
                client.services()
                        .inNamespace(deployment.getMetadata().getNamespace())
                        .withName(deployment.getMetadata().getName() + "-service")
                        .delete();
                System.out.println("service deleted successfully");
            }
        }
    }
}

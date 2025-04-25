package org.acme;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.ArrayList;
import java.util.List;

public class NesTopologyReconciler implements Reconciler<NesTopology> {

    io.fabric8.kubernetes.client.KubernetesClient client;

    public NesTopologyReconciler(io.fabric8.kubernetes.client.KubernetesClient client) {
        this.client = client;
    }

    public UpdateControl<NesTopology> reconcile(NesTopology primary, Context<NesTopology> context) {
        // Create a deployment and service for each worker
        for (Container container : createContainers(primary)) {
            createDeployment(primary, container);
            createService(primary, container);
        }

        return UpdateControl.noUpdate();
    }

    public List<Container> createContainers(NesTopology primary) {
        List<Container> containers = new ArrayList<>();
        for (WorkerSpec workerSpec : primary.getSpec().getWorkerSpecs()) {
            Container container = new Container();
            container.setName(workerSpec.getName());
            container.setImage(workerSpec.getImage());
            container.setArgs(workerSpec.getArgs());
            containers.add(container);
            System.out.println(" - " + container.getName() + " : " + container.getImage());
        }
        return containers;
    }

    public void createDeployment(NesTopology primary, Container container) {
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
        client.apps().deployments().inNamespace(primary.getMetadata().getNamespace()).createOrReplace(deployment);
        System.out.println("deployment created successfully");
    }

    public void createService(NesTopology primary, Container container) {
        Service service = new ServiceBuilder()
                .withNewMetadata().withName(container.getName() + "-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", container.getName())
                .addNewPort()
                .withPort(80)
                .withNewTargetPort(8080)
                .endPort()
                .withType("ClusterIP") // we only communicate inside cluster
                .endSpec()
                .build();

        System.out.println("creating service...: " + service.getMetadata().getName());
        client.services().inNamespace(primary.getMetadata().getNamespace()).createOrReplace(service);
        System.out.println("service created successfully");
    }
}

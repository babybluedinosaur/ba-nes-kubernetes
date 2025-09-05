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
    private static final String namespace = "default";
    io.fabric8.kubernetes.client.KubernetesClient client;
    List<HasMetadata> resources;
    Map<String, NesWorker> workerMap;

    public NesTopologyReconciler(io.fabric8.kubernetes.client.KubernetesClient client) {
        this.client = client;
        this.resources = new ArrayList<>();
        this.workerMap = new HashMap<>(); // Contains latest workerSpecs
    }

    // Create a deployment and service for each worker
    public UpdateControl<NesTopology> reconcile(NesTopology desired, Context<NesTopology> context) throws IOException {
        logger.info("Starting reconcile");
        resources.clear();
        ConfigBuilder configBuilder = new ConfigBuilder();
        configBuilder.buildSourceMap(desired, client);

        for (Container container : createContainers(desired)) {
            createService(container);
            createDeployment(container);
        }
        client.resourceList(resources).inNamespace(namespace).createOrReplace();

        // Build target map after creating services, services are needed in converted topology
        configBuilder.buildTargetMap(client);
        cleanup(desired);
        return UpdateControl.noUpdate();
    }

    public List<Container> createContainers(NesTopology desired) {
        List<Container> containers = new ArrayList<>();
        if (desired.getSpec() == null || desired.getSpec().getWorkerNodes() == null) {
            return containers;
        }
        for (NesWorker worker : desired.getSpec().getWorkerNodes()) {
            String name = worker.getHost();
            workerMap.put(name, worker); // Duplicates get overwritten
            Container container = new Container();
            container.setName(name);
            container.setImage(worker.getImage());
            container.setImagePullPolicy("IfNotPresent");
            container.setArgs(setArguments(worker));
            container.setPorts(Arrays.asList(
                    new ContainerPortBuilder().withContainerPort(8080).build(),
                    new ContainerPortBuilder().withContainerPort(9090).build()
            ));
            containers.add(container);
        }
        return containers;
    }

    public List<String> setArguments(NesWorker worker) {
        List<String> args = new ArrayList<>();
        Set<String> nonArgumentProperties = Set.of("downstreamNodes", "upstreamNodes", "capacity");

        // Attention: we put all configs after config capacity, as args into the worker
        // The order in the topology yaml should be basically (top to bottom): config non-args, capacity, config args
        for (String configName : worker.getAdditionalProperties().keySet()) {
            if (!nonArgumentProperties.contains(configName)) {
                args.add("--" + configName + "=" + worker.getAdditionalProperties().get(configName));
            }
        }

        args.add(worker.getBind());
        args.add(worker.getConnection() + worker.getHost() + "-service:9090");
        return args;
    }

    public void createDeployment(Container container) {
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
                .withTerminationGracePeriodSeconds(3L)
                .addToContainers(container)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        resources.add(deployment);
    }

    public void createService(Container container) {
        String name = container.getName();
        if (client.services().inNamespace(namespace).withName(name + "-service").get() != null) {
            return;
        }

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

        resources.add(service);
    }

    // Delete obsolete deployments and services by comparing current state with the desired state
    public void cleanup(NesTopology desired) {
        List<Deployment> currentDeployments = client.apps().deployments()
                .inNamespace(namespace)
                .withLabel("nes", "worker")
                .list()
                .getItems();

        Set<String> desiredNames = new HashSet<>();

        // Collect workers which should not get deleted
        if (desired.getSpec() != null) {
            List<NesWorker> workers = desired.getSpec().getWorkerNodes();
            if (workers != null) {
                for (NesWorker worker : workers) {
                    desiredNames.add(worker.getHost());
                }
            }
        }

        // Delete only worker deployments, which are not in desired topology
        for (Deployment deployment : currentDeployments) {
            String deploymentName = deployment.getMetadata().getName();
            if (deploymentName.startsWith("worker") && !desiredNames.contains(deploymentName)) {
                deleteDeployment(deploymentName);
                deleteService(deploymentName);
            }
        }

    }

    private void deleteDeployment(String deploymentName) {
        logger.info("deleting deployment...: {}", deploymentName);
        client.apps().deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .delete();
        logger.info("deployment deleted successfully");
    }

    private void deleteService(String deploymentName) {
        String serviceName = deploymentName + "-service";
        logger.info("deleting service...: {}", serviceName);
        client.services()
                .inNamespace(namespace)
                .withName(deploymentName + serviceName)
                .delete();
        logger.info("service deleted successfully");
    }
}

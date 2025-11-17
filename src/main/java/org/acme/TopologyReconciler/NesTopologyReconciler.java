package org.acme.TopologyReconciler;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.acme.TopologyReconciler.Utils.ConfigBuilder;
import org.acme.TopologyReconciler.Utils.FileMounter;
import org.acme.TopologyReconciler.Worker.NesWorker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.util.*;

public class NesTopologyReconciler implements Reconciler<NesTopology> {

    private static final Logger logger = LogManager.getLogger(NesTopologyReconciler.class);
    private static final String namespace = "default";
    private static final int podCheckDelay = 2000;
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
        String crName = desired.getMetadata().getName();
        ConfigBuilder configBuilder = new ConfigBuilder();
        configBuilder.buildSourceMap(desired, client);

        for (Container container : createContainers(desired)) {
            createService(container);
            createDeployment(container, crName);
        }
        client.resourceList(resources).inNamespace(namespace).createOrReplace();

        // Build target map after creating services, services are needed in converted topology
        configBuilder.buildTargetMap(client);
        cleanup(desired);

        UpdateControl<NesTopology> statusUpdate = setPodsInStatus(desired, crName);
        if (desired.getStatus().getReadyWorkers() < desired.getStatus().getWorkers()) {
            return statusUpdate.rescheduleAfter(podCheckDelay);
        }

        return statusUpdate;
    }

    // Sets the number of ready pods, idea is to allow users to see number of ready pods in corresponding CR
    public UpdateControl<NesTopology> setPodsInStatus(NesTopology cr, String crName) {
        List<Pod> pods = client.pods()
                .inNamespace(namespace)
                .withLabel("cr", crName)
                .list()
                .getItems();

        int totalPods = pods.size();
        int readyPods = 0;
        for (Pod pod : pods) {
            if ("Running".equals(pod.getStatus().getPhase())) {
                readyPods++;
            }
        }

        NesTopologyStatus newStatus = new NesTopologyStatus();
        newStatus.setWorkers(totalPods);
        newStatus.setReadyWorkers(readyPods);

        NesTopologyStatus oldStatus = cr.getStatus();

        // compare with old status
        if (oldStatus != null
                && oldStatus.getWorkers() == newStatus.getWorkers()
                && oldStatus.getReadyWorkers() == newStatus.getReadyWorkers()) {
            return UpdateControl.noUpdate();
        }

        logger.info("Updating status: {}/{} pods ready for cr {}", readyPods, totalPods, crName);
        cr.setStatus(newStatus);
        return UpdateControl.patchStatus(cr);
    }



    public List<Container> createContainers(NesTopology desired) {
        String resourceName = desired.getMetadata().getName();
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
            if (resourceName.startsWith("file")) {
                container.setVolumeMounts(
                        Collections.singletonList(
                                FileMounter.createVolumeMount()
                        )
                );
            }

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

    public void createDeployment(Container container, String crName) {
        boolean hasMounts = container.getVolumeMounts() != null;
        String name = container.getName();
        Map<String, String> labels = new HashMap<>();
        labels.put("app", name);
        labels.put("nes", "worker");
        labels.put("cr", crName);

        PodSpecBuilder podSpecBuilder = new PodSpecBuilder()
                .withContainers(container)
                .withTerminationGracePeriodSeconds(3L);
        if (hasMounts) {
            podSpecBuilder = podSpecBuilder.withVolumes(FileMounter.createVolume());
        }
        PodSpec podSpec = podSpecBuilder.build();
        PodTemplateSpec podTemplate = new PodTemplateSpecBuilder()
                .withMetadata(new ObjectMetaBuilder().withLabels(labels).build())
                .withSpec(podSpec)
                .build();

        DeploymentBuilder deploymentBuilder = new DeploymentBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec()
                .withNewSelector().addToMatchLabels(labels).endSelector()
                .withTemplate(podTemplate)
                .endSpec();
        Deployment deployment = deploymentBuilder.build();

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

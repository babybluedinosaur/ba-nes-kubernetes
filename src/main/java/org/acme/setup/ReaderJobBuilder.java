package org.acme.setup;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import java.util.Arrays;
import java.util.List;

public class ReaderJobBuilder {

    io.fabric8.kubernetes.client.KubernetesClient client;

    public ReaderJobBuilder(io.fabric8.kubernetes.client.KubernetesClient client) {
        this.client = client;
        Container container = createContainer();
        createDeployment(container);

    }

    private Container createContainer() {
        Container container = new Container();
        container.setName("nebuli-queries-reader");
        container.setImage("busybox");
        container.setImagePullPolicy("Always");
        container.setCommand(Arrays.asList(
                "sleep",
                "infinity"));

        VolumeMount volumeMount = new VolumeMount();
        volumeMount.setName("queries-volume");
        volumeMount.setMountPath("/tmp");
        container.setVolumeMounts(List.of(volumeMount));
        return container;
    }

    private Volume volumeReference() {
        Volume volume = new VolumeBuilder()
                .withName("queries-volume")
                .withPersistentVolumeClaim(
                        new PersistentVolumeClaimVolumeSourceBuilder()
                            .withClaimName("queries-volume")
                            .build()
                )
                .build();
        return volume;
    }

    private void createDeployment(Container container) {
        String name = container.getName();
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec()
                .withNewSelector().addToMatchLabels("app", name).endSelector()
                .withNewTemplate()
                .withNewMetadata().addToLabels("app", name).endMetadata()
                .withNewSpec()
                .addToContainers(container)
                .withVolumes(List.of(volumeReference()))
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        try {
            client.apps().deployments().inNamespace("default").createOrReplace(deployment);
        } catch (Exception e) {
            System.out.println("error creating deployment: " + e.getMessage());
        }
    }
}

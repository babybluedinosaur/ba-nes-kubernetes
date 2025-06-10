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

import java.io.IOException;

public class NesQueryReconciler implements Reconciler<NesQuery> {

    io.fabric8.kubernetes.client.KubernetesClient client;
    int id = 0;

    public NesQueryReconciler(KubernetesClient client) {
        this.client = client;
    }

    // TODO: Insert cleanup
    public UpdateControl<NesQuery> reconcile(NesQuery desired, Context<NesQuery> context) throws Exception {
        Nebuli nebuli = desired.getSpec().getNebuli();
        String query = desired.getSpec().getQuery();
        String arg = nebuli.getArgs();
        if (arg.equals("start")) {
            Deployment deployment = createNebuli(nebuli, query);
            try {
                client.apps().deployments().inNamespace(desired.getMetadata().getNamespace()).createOrReplace(deployment);
            } catch (Exception e) {
                System.out.println("error creating deployment: " + e.getMessage());
            }
        } else if (arg.equals("stop")) {
            stopNebuli(nebuli);
        } else {
            throw new Exception("Unsupported nebuli argument.");
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

    public Deployment createDeployment(Nebuli nebuli, Container container) {
        String name = container.getName();
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName("query-" + ++id).endMetadata()
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

    private void stopNebuli(Nebuli nebuli) throws Exception {

    }
}

package org.acme;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;

import java.util.List;

@Workflow(dependents = {@Dependent(type = ConfigMapDependentResource.class)})
public class NesTopologyReconciler implements Reconciler<NesTopology> {

    io.fabric8.kubernetes.client.KubernetesClient client;

    public NesTopologyReconciler(io.fabric8.kubernetes.client.KubernetesClient client) {
        this.client = client;
    }

    public UpdateControl<NesTopology> reconcile(NesTopology primary,
                                                Context<NesTopology> context) {

        String name = primary.getMetadata().getName();
        String image = primary.getSpec().getImage();
        String logLevel = primary.getSpec().getLogLevel();
        List<String> args = primary.getSpec().getArgs();
//        int replicas = primary.getSpec().getReplicas();

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec()
                .withNewSelector().addToMatchLabels("app", name).endSelector()
                .withNewTemplate()
                .withNewMetadata().addToLabels("app", name).endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage(image)
                .withArgs(args.toArray(new String[0]))
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        client.apps().deployments().inNamespace(primary.getMetadata().getNamespace())
                .createOrReplace(deployment);

//        return UpdateControl.patchStatus(primary);
        return UpdateControl.noUpdate();
    }
}

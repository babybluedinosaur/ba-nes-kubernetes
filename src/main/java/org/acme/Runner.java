package org.acme;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import org.acme.QueryReconciler.NesQueryReconciler;
import org.acme.TopologyReconciler.NesTopologyReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    public static void main(String[] args) {
        DefaultKubernetesClient client = new DefaultKubernetesClient();
        Operator operator = new Operator();
        operator.register(new NesTopologyReconciler(client));
        operator.register(new NesQueryReconciler(client));
        operator.start();
        log.info("Operator started.");
    }
}

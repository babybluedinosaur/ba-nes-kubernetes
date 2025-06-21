package org.acme;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;

import java.util.HashSet;
import java.util.Set;

public class Benchmark {

    io.fabric8.kubernetes.client.KubernetesClient client;

    public Benchmark(KubernetesClient client) {
        this.client = client;
    }

    public void benchmark() {
        Set<String> podNames = new HashSet<>();
        for (Pod pod : client.pods().list().getItems()) {
            podNames.add(pod.getMetadata().getName());
        }


        client.pods().inNamespace(client.getNamespace())
                .withLabel("nes","worker")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                    }

                    @Override
                    public void onClose(WatcherException e) {

                    }
                });
    }

    boolean isReady(Pod pod) {
        if (!pod.getStatus().getPhase().equals("Running"))
            return false;

        return true;
    }
}

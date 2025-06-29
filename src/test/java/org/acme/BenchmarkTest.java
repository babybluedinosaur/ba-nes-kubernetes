package org.acme;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BenchmarkTest {

    private static final Logger logger = LogManager.getLogger(BenchmarkTest.class);
    private static final int TIMEOUT_MINUTES = 5;
    io.fabric8.kubernetes.client.KubernetesClient client = new DefaultKubernetesClient();
    DescriptiveStatistics readyStats = new DescriptiveStatistics();
    DescriptiveStatistics deleteStats = new DescriptiveStatistics();
    List<HasMetadata> resources;
    Set<String> readyPodNames;
    String topologyName = "";
    volatile Instant readyStartTime;
    volatile Instant readyDeleteTime;
    int expectedPods = 0;

    public BenchmarkTest() {
    }

    public void init(String topologyName) {
        this.client = new DefaultKubernetesClient();
        this.readyPodNames = Collections.synchronizedSet(new HashSet<>());
        this.topologyName = topologyName;
        this.expectedPods = Integer.parseInt(topologyName.split("-")[0]);
    }

    @AfterEach
    public void tearDown() throws InterruptedException, IOException {
        if (client != null) {
            logger.info("closing client");
            client.close();
            Thread.sleep(1000);
        }
    }

    @ParameterizedTest
//    @ValueSource(strings = {"1-node","2-nodes", "4-nodes", "8-nodes", "16-nodes", "32-nodes"})
    @ValueSource(strings = {"1-node"})
    public void measureTopologyTime(String topologyName) throws IOException, InterruptedException {
        logger.info("starting topology benchmark test for " +  topologyName);
        init(topologyName);

        for (int i = 0; i < 120; i++) {
            measureReadyTime();
            Thread.sleep(1000);
            measureDeleteTime();
            client.resource(resources.removeFirst()).delete();
            Thread.sleep(1000);
        }

        writeResultToCSV();
    }

    public void measureReadyTime() throws FileNotFoundException, InterruptedException {
        // Block until all workers are ready
        CountDownLatch latch = new CountDownLatch(1);

        logger.info("calculating readiness duration...");
        Watch readyWatcher = readyStartTimeWatcher(latch);
        readyStartTime = Instant.now();

        resources = client.load(new FileInputStream(
                        "src/main/resources/cr/topologies/edgeless/" + topologyName + ".yaml"
                )
        ).createOrReplace();
        latch.await(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        readyWatcher.close();
    }

    public void measureDeleteTime() throws InterruptedException {
        // We keep benchmarking other topologies, when the current topology got deleted
        CountDownLatch latch = new CountDownLatch(1);

        logger.info("calculating delete duration...");
        Watch deleteWatcher = readyDeleteTimeWatcher(latch);
        readyDeleteTime = Instant.now();

        client.apps().deployments().withLabel("nes", "worker").delete();
        latch.await(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        deleteWatcher.close();
    }

    public boolean isReady(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }
        for (PodCondition condition : pod.getStatus().getConditions()) {
            if (condition.getType().equals("Ready") && condition.getStatus().equals("True")) {
                return true;
            }
        }
        return false;
    }

    public Watch readyStartTimeWatcher(CountDownLatch latch) {
        AtomicBoolean allPodsReady = new AtomicBoolean(false);
        Watch watcher = client.pods().inNamespace(client.getNamespace())
                .withLabel("nes", "worker")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getLabels().get("app");
                        if (action == Action.ADDED || action == Action.MODIFIED) {
                            if (isReady(pod)) {
                                readyPodNames.add(name);
                            } else {
                                readyPodNames.remove(name);
                            }
                        } else if (action == Action.DELETED) {
                            readyPodNames.remove(name);
                        }

                        // All pods are ready, stop and print time
                        if (expectedPods == readyPodNames.size()) {
                            Instant end = Instant.now();
                            allPodsReady.set(true);
                            readyStats.addValue(java.time.Duration.between(readyStartTime, end).toMillis());
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onClose(WatcherException e) {
                        if (e != null) {
                            logger.error("watcher closed with error: " + e.getMessage(), e);
                        } else {
                            logger.info("watcher closed normally");
                        }
                        latch.countDown();
                    }
                });

        return watcher;
    }

    public Watch readyDeleteTimeWatcher(CountDownLatch latch) throws InterruptedException {
        Watch watcher = client.pods().inNamespace(client.getNamespace())
                .withLabel("nes", "worker")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getLabels().get("app");
                        if (action == Action.DELETED) {
                            readyPodNames.remove(name);

                            if (readyPodNames.isEmpty()) {
                                Instant end = Instant.now();
                                deleteStats.addValue(java.time.Duration.between(readyDeleteTime, end).toMillis());
                                latch.countDown();
                            }
                        }
                    }

                    @Override
                    public void onClose(WatcherException e) {
                        logger.info("watcher closed");
                    }
                });
        return watcher;
    }

    public void writeResultToCSV() throws IOException {
        File csvFile = new File("results/benchmark_edgeless.csv");
        boolean fileExists = csvFile.exists();
        FileWriter writer = new FileWriter(csvFile, true);

        try {
            if (!fileExists) {
                writer.append("Topology," +
                        "Deploy duration(ms),Delete duration(ms)," +
                        "Deploy stdEv(ms),Delete stdEv(ms)," +
                        "expectedPods\n");
            }

            logger.info(topologyName + " " + readyStats.getMean() + " " + deleteStats.getMean() + " " + expectedPods);
            writer.append(String.format("%s,%.2f,%.2f,%.2f,%.2f,%d\n",
                    topologyName,
                    readyStats.getMean(),
                    deleteStats.getMean(),
                    readyStats.getStandardDeviation(),
                    deleteStats.getStandardDeviation(),
                    expectedPods
            ));

            System.out.println("--readiness values--");
            System.out.println("count: " + readyStats.getN());
            System.out.println("mean: " + readyStats.getMean());
            System.out.println("stdDev: " + readyStats.getStandardDeviation());
            System.out.println("min: " + readyStats.getMin());
            System.out.println("max: " + readyStats.getMax());
            System.out.println("median: " + readyStats.getPercentile(50));
            System.out.println("95th percentile: " + readyStats.getPercentile(95));
            System.out.println();
            System.out.println("--delete values--");
            System.out.println("count: " + deleteStats.getN());
            System.out.println("mean: " + deleteStats.getMean());
            System.out.println("stdDev: " + deleteStats.getStandardDeviation());
            System.out.println("min: " + deleteStats.getMin());
            System.out.println("max: " + deleteStats.getMax());
            System.out.println("median: " + deleteStats.getPercentile(50));
            System.out.println("95th percentile: " + deleteStats.getPercentile(95));

        } finally {
            writer.close();
        }
    }
}
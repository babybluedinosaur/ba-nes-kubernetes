package org.acme;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.time.Duration;
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
    private static final int warmupIterations = 5;
    private static final String namespace = "default";
    KubernetesClient client = new DefaultKubernetesClient();
    DescriptiveStatistics readyStats = new DescriptiveStatistics();
    DescriptiveStatistics deleteStats = new DescriptiveStatistics();
    List<HasMetadata> resources;
    Set<String> readyNodeNames;
    String topologyName = "";
    volatile Instant readyStartTime;
    volatile Instant readyDeleteTime;
    int delay;
    int nodeCount = 0;
    int maxIterations = 125;
    int iteration = 1;
    public BenchmarkTest() {
    }

    public void init(String topologyName) {
        if (this.client != null) {
            this.client.close();
        }
        this.client = new DefaultKubernetesClient();
        this.readyNodeNames = Collections.synchronizedSet(new HashSet<>());
        this.topologyName = topologyName;
        this.nodeCount = Integer.parseInt(topologyName.split("-")[1]);
        delay = Math.max(2000, nodeCount * 150);
    }

    @ParameterizedTest

    @ValueSource(strings = {"edgeless-1", "edgeless-2", "edgeless-4", "edgeless-8", "edgeless-16", "edgeless-32"})
    public void measureTopologyTime(String topologyName) throws IOException, InterruptedException {
        logger.info("starting topology benchmark test for " + topologyName);
        init(topologyName);

        while (iteration <= maxIterations) {
            logger.info("iteration: " + iteration + " of 125");
            measureReadyTime();
            measureDeleteTime();
            iteration++;
        }

        client.resourceList(resources).delete();

        writeResultToCSV();
    }

    public void measureReadyTime() throws FileNotFoundException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        readyNodeNames.clear();
        readyStartTime = Instant.now();

        try (Watch watcher = client.pods().inNamespace(namespace)
                .withLabel("nes", "worker")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getLabels().get("app");
                        if (action == Action.ADDED || action == Action.MODIFIED) {
                            if (isReady(pod)) {
                                readyNodeNames.add(name);
                            } else {
                                readyNodeNames.remove(name);
                            }
                        } else if (action == Action.DELETED) {
                            readyNodeNames.remove(name);
                        }

                        // All worker nodes are ready
                        if (nodeCount == readyNodeNames.size()) {
                            Instant end = Instant.now();
                            Duration duration = Duration.between(readyStartTime, end);
                            if (!isWarmup()) readyStats.addValue(duration.toMillis());
                            logger.info("all workers spawned in " + duration.toMillis() + " ms");
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
                })) {
            Thread.sleep(500);
            resources = client.load(new FileInputStream(
                            "src/main/resources/cr/topologies/edgeless/" + topologyName + ".yaml"
                    )
            ).createOrReplace();

            latch.await(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        }
    }

    public void measureDeleteTime() throws InterruptedException, FileNotFoundException {
        CountDownLatch latch = new CountDownLatch(1);
        readyDeleteTime = Instant.now();
        AtomicBoolean alreadyCompleted = new AtomicBoolean(false);

        try (Watch watcher = client.pods().inNamespace(namespace)
                .withLabel("nes", "worker")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getLabels().get("app");
                        if (action == Action.DELETED) {
                            readyNodeNames.remove(name);

                            // All worker nodes are deleted
                            if (readyNodeNames.isEmpty() && !alreadyCompleted.get()) {
                                Instant end = Instant.now();
                                alreadyCompleted.set(true);
                                Duration duration = Duration.between(readyDeleteTime, end);
                                if (!isWarmup()) deleteStats.addValue(duration.toMillis());
                                logger.info("all workers deleted in " + duration.toMillis() + " ms");
                                latch.countDown();
                            }
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
                })) {
            Thread.sleep(500);

            resources = client.load(new FileInputStream(
                            "src/main/resources/cr/topologies/edgeless/" + topologyName + "-delete.yaml"
                    )
            ).createOrReplace();

            latch.await(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            Thread.sleep(delay);
        }
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

    public boolean isWarmup() {
        if (iteration <= warmupIterations) {
            logger.info("warmup iteration: " + iteration);
            return true;
        }
        return false;
    }

    public void writeResultToCSV() throws IOException {
        File csvFile = new File("/app/results/benchmark_query_firsttimestamp.csv");
        boolean fileExists = csvFile.exists();
        FileWriter writer = new FileWriter(csvFile, true);

        try {
            if (!fileExists) {
                writer.append("Topology," +
                        "Deploy duration(ms),Delete duration(ms)," +
                        "Deploy stdEv(ms),Delete stdEv(ms)," +
                        "nodeCount\n");
            }

            logger.info(topologyName + " " + readyStats.getMean() + " " + deleteStats.getMean() + " " + nodeCount);
            writer.append(String.format("%s,%.2f,%.2f,%.2f,%.2f,%d\n",
                    topologyName,
                    readyStats.getMean(),
                    deleteStats.getMean(),
                    readyStats.getStandardDeviation(),
                    deleteStats.getStandardDeviation(),
                    nodeCount
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
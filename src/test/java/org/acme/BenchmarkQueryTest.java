package org.acme;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BenchmarkQueryTest {

    private static final Logger logger = LogManager.getLogger(BenchmarkTest.class);
    private static final String namespace = "default";
    private static final String topologyPrefix = "star-";
    private static final String sourcePrefix = "server-pod-";
    private static final int warmupIterations = 5;
    KubernetesClient client = new DefaultKubernetesClient();
    List<HasMetadata> sourceResources;
    List<HasMetadata> topologyResources;
    List<HasMetadata> queryResources;
    Set<String> readySourceNames;
    Set<String> readyNodeNames;
    DescriptiveStatistics firstTimestampStats = new DescriptiveStatistics();
    String queryName = "";
    String sinkName = "";
    volatile Instant queryStartTime;
    private int timeoutMinutes;
    private int delay;
    private int workerSpawnDelay;
    int maxIterations = 125;
    int iteration = 1;
    int nodeCount = 0;

    public BenchmarkQueryTest() {
    }

    public void init(String queryName) {
        if (this.client != null) {
            this.client.close();
        }
        this.client = new DefaultKubernetesClient();
        this.readySourceNames = Collections.synchronizedSet(new HashSet<>());
        this.readyNodeNames = Collections.synchronizedSet(new HashSet<>());
        this.queryName = queryName;
        nodeCount = Integer.parseInt(queryName.split("-")[2]);
        timeoutMinutes = nodeCount > 16 ? 5 : 3;
        delay = Math.max(3000, nodeCount * 200);
        workerSpawnDelay = nodeCount*800;
    }

    @ParameterizedTest
    @ValueSource(strings = {"query-join-1","query-join-2", "query-join-4", "query-join-8", "query-join-16", "query-join-32"})
    public void measureQueryTime(String queryName) throws IOException, InterruptedException {
        logger.info("---starting query benchmark for " + queryName + "---");
        init(queryName);

        spawnSources();
        spawnTopology();
        while (iteration <= maxIterations) {
            logger.info("iteration: " + iteration + " of 125");
            measureFirstTimestampTime();
            Thread.sleep(1000);
            deleteQuery();
            iteration++;
            Thread.sleep(2000);
        }
        deleteServices();
        deleteTopology();
        deleteSources();
        writeResultToCSV();
    }

    public void spawnSources() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String sourceName = sourcePrefix + nodeCount;
        readySourceNames.clear();

        try (Watch watcher = client.pods().inNamespace(namespace)
                .withLabel("nes", "server")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getLabels().get("app");
                        if (action == Action.ADDED || action == Action.MODIFIED) {
                            if (isReady(pod)) {
                                readySourceNames.add(name);
                            } else {
                                readySourceNames.remove(name);
                            }
                        } else if (action == Action.DELETED) {
                            readySourceNames.remove(name);
                        }

                        // All sources are ready
                        if (nodeCount == readySourceNames.size()) {
                            logger.info("all sources spawned");
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
            sourceResources = client.load(new FileInputStream(
                            "src/main/resources/physical-source/" + sourceName + ".yaml"
                    )
            ).createOrReplace();

            latch.await(timeoutMinutes, TimeUnit.MINUTES);
            Thread.sleep(delay);
        }
    }

    public void spawnTopology() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String topologyName = topologyPrefix + nodeCount;
        readyNodeNames.clear();

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
                            logger.info("all workers spawned");
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
            topologyResources = client.load(new FileInputStream(
                            "src/main/resources/cr/topologies/edge/star/" + topologyName + ".yaml"
                    )
            ).createOrReplace();

            latch.await(timeoutMinutes, TimeUnit.MINUTES);
            sinkName = getSinkName();
            Thread.sleep(workerSpawnDelay);
        }
    }

    public String getSinkName() {
        List<Pod> podList = client.pods().withLabel("app", "worker-1").list().getItems();
        if (podList.isEmpty()) {
            throw new RuntimeException("there is no worker-1 (sink)");
        }
        return podList.remove(0).getMetadata().getName();
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

    public void measureFirstTimestampTime() throws IOException, InterruptedException {
        // Block until first timestamp is received
        CountDownLatch queryReady = new CountDownLatch(1);
        CountDownLatch watcherReady = new CountDownLatch(1);

        logger.info("calculating arrival time of first timestamp in " + sinkName + " ...");
        watchLogsTimestamp(queryReady, watcherReady);
        watcherReady.await(60, TimeUnit.SECONDS);
        Thread.sleep(delay);

        queryStartTime = Instant.now();
        queryResources = client.load(new FileInputStream(
                        "src/main/resources/cr/queries/joins/" + queryName + ".yaml"
                )
        ).createOrReplace();
        boolean success = queryReady.await(timeoutMinutes, TimeUnit.MINUTES);
        if (!success) {
            logger.error("timestamp calculation timed out after " + timeoutMinutes + " minutes");
        }
    }

    public void watchLogsTimestamp(CountDownLatch queryReady, CountDownLatch watcherReady) throws InterruptedException {
        Thread watchThread = new Thread(() -> {
            try (LogWatch logWatch = client.pods()
                    .inNamespace(namespace)
                    .withName(sinkName)
                    .tailingLines(0)
                    .watchLog();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(logWatch.getOutput()))) {

                watcherReady.countDown();

                String line = "";
                boolean found = false;
                while ((line = reader.readLine()) != null) {
                    if (line.matches("[0-9,]+")) {
                        if (!found) {
                            found = true;
                            Instant timestampTime = Instant.now();
                            Duration duration = Duration.between(queryStartTime, timestampTime);
                            if (!isWarmup()) firstTimestampStats.addValue(duration.toMillis());
                            logger.info("first timestamp received in: " +
                                    duration.toMillis() + "ms");
                            queryReady.countDown();
                            break;
                        } else if (line.contains("[E]")) {
                            logger.error("query failed: " + line);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public boolean isWarmup() {
        if (iteration <= warmupIterations) {
            logger.info("warmup iteration: " + iteration);
            return true;
        }
        return false;
    }

    public void deleteQuery() throws InterruptedException {
        int maxRetries = 5;
        int retryCount = 0;
        for (HasMetadata query : queryResources) {
            client.resource(query).delete();
        }
        client.batch().v1().jobs().inNamespace(namespace).withName(queryName).delete();

        while (client.batch().v1().jobs().inNamespace(namespace).withName(queryName).get() != null) {
            Thread.sleep(2000);
        }

        while (!client.pods().inNamespace(namespace).withLabel("query","nebuli").list().getItems().isEmpty()
                && retryCount < maxRetries) {;
            Thread.sleep(2000);
            retryCount++;
            logger.info("waiting for query to terminate... attempt {}/{}", retryCount, maxRetries);
        }

        if (retryCount >= maxRetries) {
            logger.error("failed to delete query after " + maxRetries + " retries");
            forceDeleteRemainingQuery();
        }
        logger.info("query " + queryName + " deleted");
    }

    private void forceDeleteRemainingQuery() {
        try {
            List<Pod> remainingPods = client.pods().inNamespace(namespace)
                    .withLabel("query","nebuli").list().getItems();

            for (Pod pod : remainingPods) {
                logger.warn("force deleting stuck pod: {}", pod.getMetadata().getName());
                client.pods().inNamespace(namespace)
                        .withName(pod.getMetadata().getName())
                        .withGracePeriod(0)
                        .delete();
            }
            Thread.sleep(5000);
        } catch (Exception e) {
            logger.error("error during force cleanup: {}", e.getMessage());
        }
    }

    public void deleteServices() throws InterruptedException {
        List<Service> services = client.services()
                .inNamespace(namespace)
                .withLabel("topology", "nes").list().getItems();

        for (Service service : services) {
            client.services().inNamespace(namespace).withName(service.getMetadata().getName()).delete();
        }

        while (!client.services().inNamespace(namespace).withLabel("topology","nes").list().getItems().isEmpty()) {
            Thread.sleep(1000);
        }
        logger.info("services deleted");

    }

    public void deleteTopology() throws InterruptedException {
        for (HasMetadata topology : topologyResources) {
            client.resource(topology).delete();
        }
        client.apps().deployments().inNamespace(namespace).withLabel("nes", "worker").delete();

        while (!client.apps().deployments().inNamespace(namespace).withLabel("nes", "worker").list().getItems().isEmpty()) {
            Thread.sleep(1000);
        }
        logger.info("topology deleted");
    }

    public void deleteSources() throws InterruptedException {
        for (HasMetadata source : sourceResources) {
            client.resource(source).delete();
        }

        while (!client.pods().inNamespace(namespace).withLabel("nes", "server").list().getItems().isEmpty()) {
            Thread.sleep(1000);
        }
        logger.info("sources deleted");
    }

    public void writeResultToCSV() throws IOException {
        logger.info("--readiness values--");
        logger.info("count: {}", firstTimestampStats.getN());
        logger.info("mean: {}", firstTimestampStats.getMean());
        logger.info("stdDev: {}", firstTimestampStats.getStandardDeviation());
        logger.info("min: {}", firstTimestampStats.getMin());
        logger.info("max: {}", firstTimestampStats.getMax());
        logger.info("median: {}", firstTimestampStats.getPercentile(50));
        logger.info("95th percentile: {}", firstTimestampStats.getPercentile(95));

        File csvFile = new File("/app/results/benchmark_query_firsttimestamp.csv");
        boolean fileExists = csvFile.exists();
        FileWriter writer = new FileWriter(csvFile, true);

        try {
            if (!fileExists) {
                writer.append("Query," +
                        "First timestamp duration duration(ms)," +
                        "First timestamp duration stdEv(ms)," +
                        "nodeCount\n");
            }

            writer.append(String.format("%s,%.2f,%.2f,%d\n",
                    queryName,
                    firstTimestampStats.getMean(),
                    firstTimestampStats.getStandardDeviation(),
                    nodeCount
            ));

        } finally {
            writer.close();
        }
    }
}

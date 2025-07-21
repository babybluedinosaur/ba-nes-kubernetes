package org.acme;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
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
    private int timeoutMinutes;
    private int delay;
    private int workerSpawnDelay;
    KubernetesClient client = new DefaultKubernetesClient();
    List<HasMetadata> sourceResources;
    List<HasMetadata> topologyResources;
    List<HasMetadata> queryResources;
    Set<String> readySourceNames;
    Set<String> readyNodeNames;
    DescriptiveStatistics firstTimestampStats = new DescriptiveStatistics();
    DescriptiveStatistics stopQueryStats = new DescriptiveStatistics();
    String queryName = "";
    String sinkName = "";
    volatile Instant queryStartTime;
    volatile Instant queryStopTime;
    int nodeCount = 0;
    int timeoutsFirstTimestamp = 0;
    int timeoutsQueryStop = 0;

    public BenchmarkQueryTest() {
    }

    public void init(String queryName) {
        this.client = new DefaultKubernetesClient();
        this.readySourceNames = Collections.synchronizedSet(new HashSet<>());
        this.readyNodeNames = Collections.synchronizedSet(new HashSet<>());
        this.queryName = queryName;
    }

    @ParameterizedTest
    @ValueSource(strings = {"query-join-1"})
    public void measureQueryTime(String queryName) throws IOException, InterruptedException {
        logger.info("---starting query benchmark for " + queryName + "---");
        int maxIterations = 120;
        int iteration = 1;
        init(queryName);

        while (iteration <= maxIterations) {
            logger.info("iteration: " + iteration + " of 120");
            spawnSources();
            spawnTopology();
            measureFirstTimestampTime();
            measureQueryStopTime();
            deleteQuery();
            deleteServices();
            deleteTopology();
            deleteSources();
            iteration++;
            Thread.sleep(delay);
        }
        writeResultToCSV();
    }

    public void spawnSources() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        nodeCount = Integer.parseInt(queryName.split("-")[2]);
        timeoutMinutes = 2;
        delay = Math.max(3000, nodeCount * 300);
        workerSpawnDelay = nodeCount*800;
        String sourceName = sourcePrefix + nodeCount;
        readySourceNames.clear();

        try (Watch watcher = client.pods().inNamespace(client.getNamespace())
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

                        // All pods are ready
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

        try (Watch watcher = client.pods().inNamespace(client.getNamespace())
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

                        // All pods are ready
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
        List<Pod> podList = client.pods().withLabel("app", "worker1").list().getItems();
        if (podList.isEmpty()) {
            throw new RuntimeException("there is no worker1 (sink)");
        }
        return podList.removeFirst().getMetadata().getName();
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
            timeoutsFirstTimestamp++;
        }
    }

    public void measureQueryStopTime() throws FileNotFoundException, InterruptedException {
        // Block until query is stopped
        CountDownLatch queryStopped = new CountDownLatch(1);
        CountDownLatch watcherReady = new CountDownLatch(1);

        logger.info("calculating query stopping duration in " + queryName + " ...");
        watchLogsUnregister(queryStopped, watcherReady);
        watcherReady.await(60, TimeUnit.SECONDS);
        Thread.sleep(delay);

        queryStopTime = Instant.now();
        queryResources = client.load(new FileInputStream(
                        "src/main/resources/cr/queries/joins/stop-" + queryName + ".yaml"
                )
        ).createOrReplace();
        boolean success = queryStopped.await(timeoutMinutes, TimeUnit.MINUTES);
        if (!success) {
            logger.error("query stop timed out after " + timeoutMinutes + " minutes");
            timeoutsQueryStop++;
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
                            firstTimestampStats.addValue(duration.toMillis());
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

    public void watchLogsUnregister(CountDownLatch queryStopped, CountDownLatch watcherReady) throws InterruptedException {
        Thread watchThread = new Thread(() -> {
            try (LogWatch logWatch = client.pods()
                    .inNamespace(namespace)
                    .withName(sinkName)
                    .tailingLines(50)
                    .watchLog();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(logWatch.getOutput()))) {

                watcherReady.countDown();

                String line = "";
                while ((line = reader.readLine()) != null) {
                    if (line.contains("[unregisterQuery]")) {
                        Instant unregisterTime = Instant.now();
                        Duration duration = Duration.between(queryStopTime, unregisterTime);
                        stopQueryStats.addValue(duration.toMillis());
                        logger.info("query stopped in: " +
                                duration.toMillis() + "ms");
                        queryStopped.countDown();
                        break;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public void deleteQuery() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Set<String> deletedQueries = new HashSet<>();

        try (Watch watcher = client.pods().inNamespace(client.getNamespace())
                .withLabel("query", "nebuli")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getLabels().get("app");
                        if (action == Action.DELETED) {
                            deletedQueries.add(name);
                        }

                        List<Deployment> query = client.apps().deployments()
                                .withLabel("query", "nebuli")
                                .list().getItems();

                        // All queries are deleted
                        if (deletedQueries.size() == 1 && query.isEmpty()) {
                            logger.info("all queries deleted");
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
            Thread.sleep(1000);
            for (HasMetadata query : queryResources) {
                client.resource(query).delete();
            }
            latch.await(timeoutMinutes, TimeUnit.MINUTES);
            Thread.sleep(delay);
        }
    }

    public void deleteServices() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Set<String> deletedServices = new HashSet<>();

        try (Watch watcher = client.services().inNamespace(client.getNamespace())
                .withLabel("topology", "nes")
                .watch(new Watcher<Service>() {
                    @Override
                    public void eventReceived(Action action, Service service) {
                        String name = service.getMetadata().getName();
                        if (action == Action.DELETED) {
                            deletedServices.add(name);
                        }

                        List<Service> services = client.services()
                                .inNamespace(namespace)
                                .withLabel("topology", "nes")
                                .list().getItems();

                        // All services are deleted, two times because every worker and server has a service
                        if ((2 * nodeCount) == deletedServices.size() && services.isEmpty()) {
                            logger.info("all services deleted");
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
            Thread.sleep(1000);
            List<Service> services = client.services()
                    .inNamespace(namespace)
                    .withLabel("topology", "nes").list().getItems();

            for (Service service : services) {
                client.services().inNamespace(namespace).withName(service.getMetadata().getName()).delete();
            }
            latch.await(timeoutMinutes, TimeUnit.MINUTES);
            Thread.sleep(delay);
        }
    }

    public void deleteTopology() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Set<String> deletedNodes = new HashSet<>();

        try (Watch watcher = client.pods().inNamespace(client.getNamespace())
                .withLabel("nes", "worker")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getLabels().get("app");
                        if (action == Action.DELETED) {
                            deletedNodes.add(name);
                        }

                        List<Deployment> workers = client.apps().deployments()
                                .withLabel("nes", "worker")
                                .list().getItems();

                        // All workers are deleted
                        if (nodeCount == deletedNodes.size() && workers.isEmpty()) {
                            logger.info("all workers deleted");
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
            Thread.sleep(1000);
            for (HasMetadata topology : topologyResources) {
                client.resource(topology).delete();
            }
            client.apps().deployments().inNamespace(namespace).withLabel("nes", "worker").delete();
            latch.await(timeoutMinutes, TimeUnit.MINUTES);
            Thread.sleep(delay);
        }
    }

    public void deleteSources() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Set<String> deletedSources = new HashSet<>();

        try (Watch watcher = client.pods().inNamespace(client.getNamespace())
                .withLabel("nes", "server")
                .watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getLabels().get("app");
                        if (action == Action.DELETED) {
                            deletedSources.add(name);
                        }

                        List<Pod> pods = client.pods()
                                .inNamespace(client.getNamespace())
                                .withLabel("nes", "server")
                                .list().getItems();

                        // All sources are deleted
                        if (nodeCount == deletedSources.size() && pods.isEmpty()) {
                            logger.info("all sources deleted");
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
            Thread.sleep(1000);
            for (HasMetadata source : sourceResources) {
                client.resource(source).delete();
            }
            latch.await(timeoutMinutes, TimeUnit.MINUTES);
            Thread.sleep(delay);
        }
    }

    public void writeResultToCSV() throws IOException {
        File csvFile = new File("results/benchmark_query.csv");
        boolean fileExists = csvFile.exists();
        FileWriter writer = new FileWriter(csvFile, true);

        try {
            if (!fileExists) {
                writer.append("Query," +
                        "First timestamp duration duration(ms),Query stop duration(ms)," +
                        "First timestamp duration stdEv(ms),Query stop stdEv(ms)," +
                        "nodeCount\n");
            }

            logger.info(queryName + " " + firstTimestampStats.getMean() + " " + stopQueryStats.getMean() + " " + nodeCount);
            writer.append(String.format("%s,%.2f,%.2f,%.2f,%.2f,%d,%d,%d\n",
                    queryName,
                    firstTimestampStats.getMean(),
                    stopQueryStats.getMean(),
                    firstTimestampStats.getStandardDeviation(),
                    stopQueryStats.getStandardDeviation(),
                    nodeCount,
                    timeoutsFirstTimestamp,
                    timeoutsQueryStop
            ));

            System.out.println("--readiness values--");
            System.out.println("count: " + firstTimestampStats.getN());
            System.out.println("mean: " + firstTimestampStats.getMean());
            System.out.println("stdDev: " + firstTimestampStats.getStandardDeviation());
            System.out.println("min: " + firstTimestampStats.getMin());
            System.out.println("max: " + firstTimestampStats.getMax());
            System.out.println("median: " + firstTimestampStats.getPercentile(50));
            System.out.println("95th percentile: " + firstTimestampStats.getPercentile(95));
            System.out.println();
            System.out.println("--delete values--");
            System.out.println("count: " + stopQueryStats.getN());
            System.out.println("mean: " + stopQueryStats.getMean());
            System.out.println("stdDev: " + stopQueryStats.getStandardDeviation());
            System.out.println("min: " + stopQueryStats.getMin());
            System.out.println("max: " + stopQueryStats.getMax());
            System.out.println("median: " + stopQueryStats.getPercentile(50));
            System.out.println("95th percentile: " + stopQueryStats.getPercentile(95));

        } finally {
            writer.close();
        }
    }
}

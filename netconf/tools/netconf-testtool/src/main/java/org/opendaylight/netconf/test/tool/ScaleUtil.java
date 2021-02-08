/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import ch.qos.logback.classic.Level;
import com.google.common.base.Stopwatch;
import com.google.common.io.CharStreams;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressFBWarnings({"DM_EXIT", "DM_DEFAULT_ENCODING", "SLF4J_LOGGER_SHOULD_BE_FINAL"})
public final class ScaleUtil {
    private static ScheduledExecutorService executor;
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final Semaphore SEMAPHORE = new Semaphore(0);
    private static final Stopwatch STOPWATCH = Stopwatch.createUnstarted();

    private static final long TIMEOUT = 20L;
    private static final long RETRY_DELAY = 10L;
    private static final int DEVICE_STEP = 1000;

    private static final String RESTCONF_URL = "http://127.0.0.1:8181/restconf/operational/"
            + "network-topology:network-topology/topology/topology-netconf/";

    private static ch.qos.logback.classic.Logger root;
    private static Logger resultsLog;


    private ScaleUtil() {
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    public static void main(final String[] args) {
        final TesttoolParameters params = TesttoolParameters.parseArgs(args, TesttoolParameters.getParser());
        params.validate();
        setUpLoggers(params);
        executor = new LoggingWrapperExecutor(params.threadAmount);

        while (true) {
            root.warn("Starting scale test with {} devices", params.deviceCount);
            final ScheduledFuture<?> timeoutGuardFuture = executor.schedule(new TimeoutGuard(), TIMEOUT,
                    TimeUnit.MINUTES);

            cleanup(params);
            startKaraf(params);
            final Configuration configuration = new ConfigurationBuilder().from(params).build();
            final NetconfDeviceSimulator netconfDeviceSimulator = new NetconfDeviceSimulator(configuration);
            final List<Integer> openDevices = netconfDeviceSimulator.start();
            if (openDevices.size() == 0) {
                root.error("Failed to start any simulated devices, exiting...");
                System.exit(1);
            }
            waitNetconfTopoReady();

            root.warn("All set, starting stopwatch");
            STOPWATCH.start();

            try {
                generateConnectors(params, openDevices);

                executor.schedule(
                    new ScaleVerifyCallable(netconfDeviceSimulator, params.deviceCount), RETRY_DELAY, TimeUnit.SECONDS);
                root.warn("First callable scheduled");
                SEMAPHORE.acquire();
                root.warn("semaphore released");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            timeoutGuardFuture.cancel(false);
            params.deviceCount += DEVICE_STEP;
            netconfDeviceSimulator.close();
            STOPWATCH.reset();
        }
    }

    private static int execCommand(String command, int timeout) {
        final int failureCode = -1;

        try {
            final Process process = RUNTIME.exec(new String[]{"/bin/sh", "-c", command});
            if (timeout != -1 && !process.waitFor(timeout, TimeUnit.SECONDS)) {
                root.warn("Cmd '{}' has reached timeout", command);
                return failureCode;
            }
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                root.warn("Command '{}' has returned exit value {}", command, exitValue);
            }
            root.debug("Cmd output: {}",
                    CharStreams.toString(new BufferedReader(new InputStreamReader(process.getInputStream()))));
            String cmdErrorOutput =
                    CharStreams.toString(new BufferedReader(new InputStreamReader(process.getErrorStream())));
            if (cmdErrorOutput.length() > 0) {
                root.warn("While running cmd {} received error output: {}", cmdErrorOutput);
                return failureCode;
            }
            return exitValue;
        } catch (IOException | InterruptedException e) {
            root.warn("Cmd '{}' failed", command, e);
            return failureCode;
        }
    }

    private static void waitNetconfTopoReady() {
        root.warn("Wait for Netconf topology to be accessible via Restconf");
        Response response = requestNetconfTopology();

        while (response == null || (response.getStatusCode() != 200 && response.getStatusCode() != 204)) {
            if (response == null) {
                root.info("Failed to get response from controller, going to sleep...");
            } else {
                root.info("Received status code {}, going to sleep...", response.getStatusCode());
            }
            try {
                Thread.sleep(1000L);
                response = requestNetconfTopology();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        root.warn("Returned status code {}, Netconf topology is accessible", response.getStatusCode());
    }

    private static Response requestNetconfTopology() {
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient(new Builder()
            .setConnectTimeout(Integer.MAX_VALUE)
            .setRequestTimeout(Integer.MAX_VALUE)
            .setAllowPoolingConnections(true)
            .build());
        String RESTCONF_URL = "http://127.0.0.1:8181/restconf/operational/"
            + "network-topology:network-topology/topology/topology-netconf/";
        AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient.prepareGet(RESTCONF_URL)
                .addHeader("content-type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Basic YWRtaW46YWRtaW4=")
                .setRequestTimeout(Integer.MAX_VALUE);
        Request request = requestBuilder.build();

        try {
            return asyncHttpClient.executeRequest(request).get();
        } catch (ExecutionException e) {
            root.warn(e.getMessage());
            return null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    private static void generateConnectors(final TesttoolParameters params, final List<Integer> openDevices) throws InterruptedException {
        final ArrayList<ArrayList<Execution.DestToPayload>> allThreadsPayloads = params
            .getThreadsPayloads(openDevices);
        final ArrayList<Execution> executions = new ArrayList<>();
        for (ArrayList<Execution.DestToPayload> payloads : allThreadsPayloads) {
            executions.add(new Execution(params, payloads));
        }

        List<Future<Void>> futures = executor.invokeAll(executions, params.timeOut, TimeUnit.SECONDS);
        int threadNum = 0;
        for (Future<Void> future : futures) {
            threadNum++;
            if (future.isCancelled()) {
                root.info("{}. thread timed out.",threadNum);
            } else {
                try {
                    future.get();
                } catch (final ExecutionException | InterruptedException e) {
                    root.warn("{}. thread failed.", threadNum, e);
                    root.warn("Failed to send connection configuration");
                    System.exit(1);
                }
            }
        }
    }

    private static void setUpLoggers(final TesttoolParameters params) {
        System.setProperty("log_file_name", "scale-util.log");

        root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(params.debug ? Level.DEBUG : Level.INFO);
        resultsLog = LoggerFactory.getLogger("results");
        if (!params.debug) {
            ch.qos.logback.classic.Logger rpcHandlerLog = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger("org.opendaylight.netconf.test.tool.rpchandler");
            rpcHandlerLog.setLevel(Level.WARN);
            ch.qos.logback.classic.Logger configurationtLog = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger("org.opendaylight.netconf.test.tool.config");
            configurationtLog.setLevel(Level.WARN);
            ch.qos.logback.classic.Logger serverSessionLog = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger("org.opendaylight.netconf.shaded.sshd.server.session");
            serverSessionLog.setLevel(Level.WARN);
            ch.qos.logback.classic.Logger nettyServBootLog = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger("io.netty.bootstrap.ServerBootstrap");
            nettyServBootLog.setLevel(Level.ERROR);
        }
    }

    private static void cleanup(final TesttoolParameters params) {
        try {
            stopKaraf();
            deleteFolder(new File(params.distroFolder.getAbsoluteFile() + "/data"));
            deleteFolder(new File(params.distroFolder.getAbsoluteFile() + "/snapshots"));
            deleteFolder(new File(params.distroFolder.getAbsoluteFile() + "/segmented-journal"));

        } catch (IOException | InterruptedException e) {
            root.warn("Failed to stop karaf", e);
            System.exit(1);
        }
    }

    private static void startKaraf(final TesttoolParameters params) {
        if (params.distroFolder == null) {
            root.error("Distro folder is not set, exiting...");
            System.exit(1);
        }

        // start karaf
        try {
            RUNTIME.exec(params.distroFolder.getAbsolutePath() + "/bin/start");
            String status;
            do {
                final Process exec = RUNTIME.exec(params.distroFolder.getAbsolutePath() + "/bin/status");
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    root.warn("Failed to sleep", e);
                }
                status = CharStreams.toString(new BufferedReader(new InputStreamReader(exec.getInputStream())));
                root.warn("Current status: {}", status);
            } while (!status.startsWith("Running ..."));
            Thread.sleep(10000L);
        } catch (IOException | InterruptedException e) {
            root.warn("Failed to start karaf", e);
            System.exit(1);
        }
        root.warn("Karaf started");

        // install features
        String[] features = {"odl-netconf-connector-all", "odl-restconf-nb-bierman02"};
        String clientPath = params.distroFolder.getAbsolutePath() + "/bin/client";
        //  FIXME: remove timeout parameter "-t" from command after problem with not closing session is fixed
        // https://issues.apache.org/jira/browse/KARAF-6980
        final String featureInstallCmd = clientPath + " -u karaf -p karaf feature:install " +
                String.join(" ", features) + " -t 5000";
        root.warn("Doing feature install {}", featureInstallCmd);
        while (execCommand(featureInstallCmd, 30) != 0) {
            try {
                root.warn("Failed to install features, retry after sleep...");
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                root.warn("Failed to sleep", e);
            }
        }

        // check if all features have been successfully installed and started
        while (!featuresStarted(features, clientPath)) {
            try {
                root.warn("Not all features started yet, retry after sleep...");
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                root.warn("Failed to sleep", e);
            }
        }
    }

    private static boolean featuresStarted(String[] features, String clientPath) {
        boolean featuresStarted = true;
        for (String feature: features) {
            String checkCommand = clientPath + " \"feature:list\" -t 1000 | grep \"^" + feature + ".*Started\"";
            featuresStarted &= execCommand(checkCommand, 30) == 0;
        }
        return featuresStarted;
    }

    private static void stopKaraf() throws IOException, InterruptedException {
        root.info("Stopping karaf and sleeping for 10 sec..");
        String controllerPid = "";
        do {
            final Process pgrep = RUNTIME.exec("pgrep -f org.apache.karaf.main.Main");

            controllerPid = CharStreams.toString(new BufferedReader(new InputStreamReader(pgrep.getInputStream())));
            root.warn(controllerPid);
            RUNTIME.exec("kill -9 " + controllerPid);

            Thread.sleep(10000L);
        } while (!controllerPid.isEmpty());
    }

    private static void deleteFolder(final File folder) {
        File[] files = folder.listFiles();
        if (files != null) { //some JVMs return null for empty dirs
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    if (!f.delete()) {
                        root.warn("Failed to delete {}", f);
                    }
                }
            }
        }
        if (folder.exists() && !folder.delete()) {
            root.warn("Failed to delete {}", folder);
        }
    }

    private static class ScaleVerifyCallable implements Callable<Void> {
        private static final Logger LOG = LoggerFactory.getLogger(ScaleVerifyCallable.class);
        private static final Pattern PATTERN = Pattern.compile("connected");

        private final AsyncHttpClient asyncHttpClient = new AsyncHttpClient(new Builder()
                .setConnectTimeout(Integer.MAX_VALUE)
                .setRequestTimeout(Integer.MAX_VALUE)
                .setAllowPoolingConnections(true)
                .build());
        private final NetconfDeviceSimulator simulator;
        private final int deviceCount;
        private final Request request;

        ScaleVerifyCallable(final NetconfDeviceSimulator simulator, final int deviceCount) {
            LOG.info("New callable created");
            this.simulator = simulator;
            this.deviceCount = deviceCount;
            AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient.prepareGet(RESTCONF_URL)
                    .addHeader("content-type", "application/xml")
                    .addHeader("Accept", "application/xml")
                    .addHeader("Authorization", "Basic YWRtaW46YWRtaW4=")
                    .setRequestTimeout(Integer.MAX_VALUE);
            request = requestBuilder.build();
        }

        @Override
        public Void call() throws Exception {
            try {
                final Response response = asyncHttpClient.executeRequest(request).get();

                if (response.getStatusCode() != 200 && response.getStatusCode() != 204) {
                    LOG.warn("Request failed, status code: {}", response.getStatusCode() + response.getStatusText());
                    executor.schedule(new ScaleVerifyCallable(simulator, deviceCount), RETRY_DELAY, TimeUnit.SECONDS);
                } else {
                    final String body = response.getResponseBody();
                    final Matcher matcher = PATTERN.matcher(body);
                    int count = 0;
                    while (matcher.find()) {
                        count++;
                    }
                    root.warn("Currently connected devices : {} out of {}, time elapsed: {}",
                            count, deviceCount, STOPWATCH);
                    resultsLog.info("Currently connected devices : {} out of {}, time elapsed: {}",
                        count, deviceCount, STOPWATCH);
                    if (count != deviceCount) {
                        executor.schedule(
                            new ScaleVerifyCallable(simulator, deviceCount), RETRY_DELAY, TimeUnit.SECONDS);
                    } else {
                        STOPWATCH.stop();
                        root.warn("All devices connected in {}", STOPWATCH);
                        resultsLog.info("All devices connected in {}", STOPWATCH);
                        SEMAPHORE.release();
                    }
                }
            } catch (ConnectException | ExecutionException e) {
                LOG.warn("Failed to connect to Restconf, is the controller running?", e);
                executor.schedule(new ScaleVerifyCallable(simulator, deviceCount), RETRY_DELAY, TimeUnit.SECONDS);
            }
            return null;
        }
    }

    private static class TimeoutGuard implements Callable<Void> {
        @Override
        public Void call() {
            resultsLog.warn("Timeout for scale test reached after: {} ..aborting", STOPWATCH);
            root.warn("Timeout for scale test reached after: {} ..aborting", STOPWATCH);
            System.exit(0);
            return null;
        }
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    public static class LoggingWrapperExecutor extends ScheduledThreadPoolExecutor {
        public LoggingWrapperExecutor(final int corePoolSize) {
            super(corePoolSize);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
            return super.schedule(new LogOnExceptionCallable<>(callable), delay, unit);
        }

        private static class LogOnExceptionCallable<T> implements Callable<T> {
            private final Callable<T> theCallable;

            LogOnExceptionCallable(final Callable<T> theCallable) {
                this.theCallable = theCallable;
            }

            @Override
            public T call() {
                try {
                    return theCallable.call();
                } catch (Exception e) {
                    // log
                    root.warn("error in executing: " + theCallable + ". It will no longer be run!", e);

                    // rethrow so that the executor can do it's thing
                    throw new RuntimeException(e);
                }
            }
        }
    }
}

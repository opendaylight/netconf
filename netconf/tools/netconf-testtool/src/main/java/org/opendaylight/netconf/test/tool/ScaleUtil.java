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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScaleUtil {
    private static final ScheduledExecutorService EXECUTOR = new LoggingWrapperExecutor(4);
    private static final Semaphore SEMAPHORE = new Semaphore(0);
    private static final Stopwatch STOPWATCH = Stopwatch.createUnstarted();

    private static final long TIMEOUT = 20L;
    private static final long RETRY_DELAY = 10L;
    private static final int DEVICE_STEP = 1000;

    private static ch.qos.logback.classic.Logger root;
    private static Logger resultsLog;

    @SuppressWarnings("checkstyle:illegalCatch")
    public static void main(final String[] args) {
        final TesttoolParameters params = TesttoolParameters.parseArgs(args, TesttoolParameters.getParser());

        setUpLoggers(params);

        // cleanup at the start in case controller was already running
        final Runtime runtime = Runtime.getRuntime();
        cleanup(runtime, params);

        while (true) {
            root.warn("Starting scale test with {} devices", params.deviceCount);
            final ScheduledFuture timeoutGuardFuture = EXECUTOR.schedule(new TimeoutGuard(), TIMEOUT, TimeUnit.MINUTES);
            final Configuration configuration = new ConfigurationBuilder().from(params).build();
            final NetconfDeviceSimulator netconfDeviceSimulator = new NetconfDeviceSimulator(configuration);
            try {
                final List<Integer> openDevices = netconfDeviceSimulator.start();
                if (openDevices.size() == 0) {
                    root.error("Failed to start any simulated devices, exiting...");
                    System.exit(1);
                }
                if (params.distroFolder != null) {
                    final Main.ConfigGenerator configGenerator = new Main.ConfigGenerator(
                        params.distroFolder, openDevices);
                    final List<File> generated = configGenerator.generate(
                            params.ssh, params.generateConfigBatchSize,
                            params.generateConfigsTimeout, params.generateConfigsAddress,
                            params.devicesPerPort);
                    configGenerator.updateFeatureFile(generated);
                    configGenerator.changeLoadOrder();
                }
            } catch (final Exception e) {
                root.error("Unhandled exception", e);
                netconfDeviceSimulator.close();
                System.exit(1);
            }

            root.warn(params.distroFolder.getAbsolutePath());
            try {
                runtime.exec(params.distroFolder.getAbsolutePath() + "/bin/start");
                String status;
                do {
                    final Process exec = runtime.exec(params.distroFolder.getAbsolutePath() + "/bin/status");
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException e) {
                        root.warn("Failed to sleep", e);
                    }
                    status = CharStreams.toString(new BufferedReader(new InputStreamReader(exec.getInputStream())));
                    root.warn("Current status: {}", status);
                } while (!status.startsWith("Running ..."));
                root.warn("Doing feature install {}", params.distroFolder.getAbsolutePath()
                    + "/bin/client -u karaf feature:install odl-restconf-noauth odl-netconf-connector-all");
                final Process featureInstall = runtime.exec(params.distroFolder.getAbsolutePath()
                    + "/bin/client -u karaf feature:install odl-restconf-noauth odl-netconf-connector-all");
                root.warn(
                    CharStreams.toString(new BufferedReader(new InputStreamReader(featureInstall.getInputStream()))));
                root.warn(
                    CharStreams.toString(new BufferedReader(new InputStreamReader(featureInstall.getErrorStream()))));

            } catch (IOException e) {
                root.warn("Failed to start karaf", e);
                System.exit(1);
            }

            root.warn("Karaf started, starting stopwatch");
            STOPWATCH.start();

            try {
                EXECUTOR.schedule(
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

            cleanup(runtime, params);
        }
    }

    private static void setUpLoggers(final TesttoolParameters params) {
        System.setProperty("log_file_name", "scale-util.log");

        root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(params.debug ? Level.DEBUG : Level.INFO);
        resultsLog = LoggerFactory.getLogger("results");
    }

    private static void cleanup(final Runtime runtime, final TesttoolParameters params) {
        try {
            stopKaraf(runtime, params);
            deleteFolder(new File(params.distroFolder.getAbsoluteFile() + "/data"));

        } catch (IOException | InterruptedException e) {
            root.warn("Failed to stop karaf", e);
            System.exit(1);
        }
    }

    private static void stopKaraf(final Runtime runtime, final TesttoolParameters params)
            throws IOException, InterruptedException {
        root.info("Stopping karaf and sleeping for 10 sec..");
        String controllerPid = "";
        do {
            final Process pgrep = runtime.exec("pgrep -f org.apache.karaf.main.Main");

            controllerPid = CharStreams.toString(new BufferedReader(new InputStreamReader(pgrep.getInputStream())));
            root.warn(controllerPid);
            runtime.exec("kill -9 " + controllerPid);

            Thread.sleep(10000L);
        } while (!controllerPid.isEmpty());
        deleteFolder(new File(params.distroFolder.getAbsoluteFile() + "/data"));
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) { //some JVMs return null for empty dirs
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    private static TesttoolParameters parseArgs(final String[] args, final ArgumentParser parser) {
        final TesttoolParameters parameters = new TesttoolParameters();
        try {
            parser.parseArgs(args, parameters);
            return parameters;
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        }

        System.exit(1);
        return null;
    }

    private static class ScaleVerifyCallable implements Callable {
        private static final Logger LOG = LoggerFactory.getLogger(ScaleVerifyCallable.class);

        private static final String RESTCONF_URL
                = "http://127.0.0.1:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/";
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
                    .setRequestTimeout(Integer.MAX_VALUE);
            request = requestBuilder.build();
        }

        @Override
        public Object call() throws Exception {
            try {
                final Response response = asyncHttpClient.executeRequest(request).get();

                if (response.getStatusCode() != 200 && response.getStatusCode() != 204) {
                    LOG.warn("Request failed, status code: {}", response.getStatusCode() + response.getStatusText());
                    EXECUTOR.schedule(new ScaleVerifyCallable(simulator, deviceCount), RETRY_DELAY, TimeUnit.SECONDS);
                } else {
                    final String body = response.getResponseBody();
                    final Matcher matcher = PATTERN.matcher(body);
                    int count = 0;
                    while (matcher.find()) {
                        count++;
                    }
                    resultsLog.info("Currently connected devices : {} out of {}, time elapsed: {}",
                        count, deviceCount + 1, STOPWATCH);
                    if (count != deviceCount + 1) {
                        EXECUTOR.schedule(
                            new ScaleVerifyCallable(simulator, deviceCount), RETRY_DELAY, TimeUnit.SECONDS);
                    } else {
                        STOPWATCH.stop();
                        resultsLog.info("All devices connected in {}", STOPWATCH);
                        SEMAPHORE.release();
                    }
                }
            } catch (ConnectException | ExecutionException e) {
                LOG.warn("Failed to connect to Restconf, is the controller running?", e);
                EXECUTOR.schedule(new ScaleVerifyCallable(simulator, deviceCount), RETRY_DELAY, TimeUnit.SECONDS);
            }
            return null;
        }
    }

    private static class TimeoutGuard implements Callable {
        @Override
        public Object call() throws Exception {
            resultsLog.warn("Timeout for scale test reached after: {} ..aborting", STOPWATCH);
            root.warn("Timeout for scale test reached after: {} ..aborting", STOPWATCH);
            System.exit(0);
            return null;
        }
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    public static class LoggingWrapperExecutor extends ScheduledThreadPoolExecutor {
        public LoggingWrapperExecutor(int corePoolSize) {
            super(corePoolSize);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return super.schedule(wrapCallable(callable), delay, unit);
        }

        private Callable wrapCallable(Callable callable) {
            return new LogOnExceptionCallable(callable);
        }

        private class LogOnExceptionCallable implements Callable {
            private Callable theCallable;

            LogOnExceptionCallable(Callable theCallable) {
                super();
                this.theCallable = theCallable;
            }

            @Override
            public Object call() throws Exception {
                try {
                    theCallable.call();
                    return null;
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

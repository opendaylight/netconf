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
import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
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
    private static final ScheduledExecutorService EXECUTOR = new LoggingWrapperExecutor(4);
    private static final Semaphore SEMAPHORE = new Semaphore(0);
    private static final Stopwatch STOPWATCH = Stopwatch.createUnstarted();
    private static final String RESTCONF_URL = "http://%s:%d/rests/data/"
            + "network-topology:network-topology?content=nonconfig";

    private static final long TIMEOUT = 20L;
    private static final long RETRY_DELAY = 10L;
    private static final int DEVICE_STEP = 1000;

    private static ch.qos.logback.classic.Logger root;
    private static Logger resultsLog;

    private ScaleUtil() {
    }

    @SuppressWarnings("checkstyle:illegalCatch")
    public static void main(final String[] args) {
        final TesttoolParameters params = TesttoolParameters.parseArgs(args, TesttoolParameters.getParser());

        setUpLoggers(params);

        // cleanup at the start in case controller was already running
        final Runtime runtime = Runtime.getRuntime();
        cleanup(runtime, params);

        while (true) {
            root.warn("Starting scale test with {} devices", params.deviceCount);
            final ScheduledFuture<?> timeoutGuardFuture = EXECUTOR.schedule(new TimeoutGuard(), TIMEOUT,
                TimeUnit.MINUTES);
            final Configuration configuration = new ConfigurationBuilder().from(params).build();
            final NetconfDeviceSimulator netconfDeviceSimulator = new NetconfDeviceSimulator(configuration);

            final List<Integer> openDevices = netconfDeviceSimulator.start();
            if (openDevices.size() == 0) {
                root.error("Failed to start any simulated devices, exiting...");
                System.exit(1);
            }

            if (params.distroFolder == null) {
                root.error("Distro folder is not set, exiting...");
                System.exit(1);
            }

            root.warn(params.distroFolder.getAbsolutePath());
            try {
                // FIXME: use ProcessBuilder and hold on to the process
                runtime.exec(new String[] {
                    params.distroFolder.getAbsolutePath() + "/bin/start"
                });
                String status;
                do {
                    final var list = runtime.exec(new String[] {
                        params.distroFolder.getAbsolutePath() + "/bin/client",
                        "feature:list"
                    });
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException e) {
                        root.warn("Failed to sleep", e);
                    }
                    status = CharStreams.toString(new BufferedReader(new InputStreamReader(list.getErrorStream())));
                    root.warn(status);
                } while (status.startsWith("Failed to get the session"));
                root.warn("Doing feature install {}", params.distroFolder.getAbsolutePath()
                    + "/bin/client feature:install odl-restconf-nb odl-netconf-topology");
                final Process featureInstall = runtime.exec(new String[] {
                    params.distroFolder.getAbsolutePath() + "/bin/client",
                    "feature:install", "odl-restconf-nb odl-netconf-topology"
                });
                root.warn(
                    CharStreams.toString(new BufferedReader(new InputStreamReader(featureInstall.getInputStream()))));
                root.warn(
                    CharStreams.toString(new BufferedReader(new InputStreamReader(featureInstall.getErrorStream()))));

            } catch (IOException e) {
                root.error("Failed to start karaf", e);
                System.exit(1);
            }

            waitNetconfTopologyReady(params);
            final Execution ex = new Execution(openDevices, params);
            ex.call();

            root.warn("Karaf started, starting stopwatch");
            STOPWATCH.start();

            try {
                EXECUTOR.schedule(new ScaleVerifyCallable(params), RETRY_DELAY, TimeUnit.SECONDS);
                root.warn("First callable scheduled");
                SEMAPHORE.acquire();
                root.warn("semaphore released");
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted while waiting for semaphore", e);
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
            deleteFolder(params.distroFolder.getAbsoluteFile().toPath().resolve("data").toFile());
        } catch (IOException | InterruptedException e) {
            root.warn("Failed to stop karaf", e);
            System.exit(1);
        }
    }

    // FIXME: use karaf/stop instead
    private static void stopKaraf(final Runtime runtime, final TesttoolParameters params)
            throws IOException, InterruptedException {
        root.info("Stopping karaf and sleeping for 10 sec..");
        String controllerPid = "";
        do {
            final var pgrep = runtime.exec(new String[] {
                "pgrep", "-f", "org.apache.karaf.main.Main"
            });

            controllerPid = CharStreams.toString(new BufferedReader(new InputStreamReader(pgrep.getInputStream())));
            root.warn(controllerPid);
            runtime.exec(new String[] {
                "kill", "-9", controllerPid
            });

            Thread.sleep(10000L);
        } while (!controllerPid.isEmpty());
    }

    private static void deleteFolder(final File folder) {
        final var files = folder.listFiles();
        if (files != null) { //some JVMs return null for empty dirs
            for (var f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else if (!f.delete()) {
                    root.warn("Failed to delete {}", f);
                }
            }
        }
        if (!folder.delete()) {
            root.warn("Failed to delete {}", folder);
        }
    }

    private static void waitNetconfTopologyReady(final TesttoolParameters params) {
        root.info("Wait for Netconf topology to be accessible via Restconf");
        HttpResponse<String> response = requestNetconfTopology(params);
        while (response == null || response.statusCode() != 200 && response.statusCode() != 204) {
            if (response == null) {
                root.warn("Failed to get response from controller, going to sleep...");
            } else {
                root.warn("Received status code {}, going to sleep...", response.statusCode());
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Sleep interrupted", e);
            }
            response = requestNetconfTopology(params);
        }
        root.info("Returned status code {}, Netconf topology is accessible", response.statusCode());
    }

    private static HttpResponse<String> requestNetconfTopology(final TesttoolParameters params) {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Integer.MAX_VALUE))
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(params.controllerAuthUsername,
                                params.controllerAuthPassword.toCharArray());
                    }
                })
                .build();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(RESTCONF_URL, params.controllerIp,
                        params.controllerPort)))
                .GET()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            root.warn(e.getMessage());
            return null;
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting for response", e);
        }
    }

    private static class ScaleVerifyCallable implements Callable<Void> {
        private static final Logger LOG = LoggerFactory.getLogger(ScaleVerifyCallable.class);
        private static final Pattern PATTERN = Pattern.compile("connected");

        private final HttpClient httpClient;
        private final HttpRequest request;

        private final int deviceCount;

        ScaleVerifyCallable(final TesttoolParameters params) {
            deviceCount = params.deviceCount;
            httpClient = HttpClient.newBuilder()
                    .authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(params.controllerAuthUsername,
                                params.controllerAuthPassword.toCharArray());
                        }
                    })
                    .build();
            request = HttpRequest.newBuilder(URI.create(String.format(RESTCONF_URL, params.controllerIp,
                            params.controllerPort)))
                    .GET()
                    .header("Content-Type", "application/xml")
                    .header("Accept", "application/xml")
                    .build();
        }

        @Override
        public Void call() throws Exception {
            LOG.info("Checking number of connected devices.");
            try {
                final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200 && response.statusCode() != 204) {
                    LOG.warn("Request failed, status code: {}", response.statusCode());
                    EXECUTOR.schedule(this, RETRY_DELAY, TimeUnit.SECONDS);
                } else {
                    final String body = response.body();
                    final Matcher matcher = PATTERN.matcher(body);
                    int count = 0;
                    while (matcher.find()) {
                        count++;
                    }
                    resultsLog.info("Currently connected devices : {} out of {}, time elapsed: {}",
                        count, deviceCount, STOPWATCH);
                    if (count != deviceCount) {
                        EXECUTOR.schedule(this, RETRY_DELAY, TimeUnit.SECONDS);
                    } else {
                        STOPWATCH.stop();
                        resultsLog.info("All {} of {} devices connected in {}", count, deviceCount, STOPWATCH);
                        SEMAPHORE.release();
                    }
                }
            } catch (ConnectException e) {
                LOG.warn("Failed to connect to Restconf, is the controller running?", e);
                EXECUTOR.schedule(this, RETRY_DELAY, TimeUnit.SECONDS);
            }
            return null;
        }
    }

    private static final class TimeoutGuard implements Callable<Void> {
        @Override
        public Void call() {
            resultsLog.warn("Timeout for scale test reached after: {} ..aborting", STOPWATCH);
            root.warn("Timeout for scale test reached after: {} ..aborting", STOPWATCH);
            System.exit(0);
            return null;
        }
    }

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
            @SuppressWarnings("checkstyle:illegalCatch")
            public T call() {
                try {
                    return theCallable.call();
                } catch (Exception e) {
                    // log
                    root.warn("error in executing: " + theCallable + ". It will no longer be run!", e);
                    Throwables.throwIfUnchecked(e);
                    // rethrow so that the executor can do it's thing
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}

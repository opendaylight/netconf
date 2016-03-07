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
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.opendaylight.netconf.test.tool.Main.Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScaleUtil {

    private static final Logger RESULTS_LOG = LoggerFactory.getLogger("results");
//    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private static final ScheduledExecutorService executor = new LoggingWrapperExecutor(4);


    private static final int deviceStep = 100;
    private static final long retryDelay = 10l;
    private static final long timeout = 10l;

    private static final Stopwatch stopwatch = Stopwatch.createUnstarted();

    private static boolean timeoutReached = false;
    private static ScheduledFuture timeoutGuardFuture;

    public static void main(final String[] args) {
        final Params params = parseArgs(args, Params.getParser());

        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(params.debug ? Level.DEBUG : Level.INFO);

        while (true) {
            root.warn("Starting scale test with {} devices", params.deviceCount);
            timeoutGuardFuture = executor.schedule(new TimeoutGuard(), timeout, TimeUnit.MINUTES);
            final NetconfDeviceSimulator netconfDeviceSimulator = new NetconfDeviceSimulator();
            try {
                final List<Integer> openDevices = netconfDeviceSimulator.start(params);
                if (openDevices.size() == 0) {
                    root.error("Failed to start any simulated devices, exiting...");
                    System.exit(1);
                }
                if (params.distroFolder != null) {
                    final Main.ConfigGenerator configGenerator = new Main.ConfigGenerator(params.distroFolder, openDevices);
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
            final Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec(params.distroFolder.getAbsolutePath() + "/bin/start");
            } catch (IOException e) {
                root.warn("Failed to start karaf", e);
                System.exit(1);
            }

            root.warn("Karaf started, starting stopwatch");
            stopwatch.start();

            synchronized (netconfDeviceSimulator) {
                try {
                    final ScheduledFuture schedule = executor.schedule(new ScaleVerifyCallable(netconfDeviceSimulator, params.deviceCount), retryDelay, TimeUnit.SECONDS);
                    root.warn("First callable scheduled");
                    netconfDeviceSimulator.wait();
                    root.warn("simulator notified");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            timeoutGuardFuture.cancel(false);
            params.deviceCount += deviceStep;
            netconfDeviceSimulator.close();
            stopwatch.reset();

            try {
                runtime.exec(params.distroFolder.getAbsolutePath() + "/bin/stop");
                Thread.sleep(10l);
            } catch (IOException | InterruptedException e) {
                root.warn("Failed to stop karaf", e);
                System.exit(1);
            }
        }


    }

    private static Params parseArgs(final String[] args, final ArgumentParser parser) {
        final Params parameters = new Params();
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

        private static final String RESTCONF_URL = "http://127.0.0.1:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/";
        private static final Pattern PATTERN = Pattern.compile("Connected");

        private final AsyncHttpClient asyncHttpClient = new AsyncHttpClient(new Builder()
                .setConnectTimeout(Integer.MAX_VALUE)
                .setRequestTimeout(Integer.MAX_VALUE)
                .setAllowPoolingConnections(true)
                .build());
        private final NetconfDeviceSimulator simulator;
        private final int deviceCount;
        private final Request request;

        public ScaleVerifyCallable(final NetconfDeviceSimulator simulator, final int deviceCount) {
            LOG.warn("New callable created");
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
            final Response response = asyncHttpClient.executeRequest(request).get();

            if (response.getStatusCode() != 200 && response.getStatusCode() != 204) {
                LOG.warn("Request failed, status code: {}", response.getStatusCode() + response.getStatusText());
                executor.schedule(new ScaleVerifyCallable(simulator, deviceCount), retryDelay, TimeUnit.SECONDS);
                LOG.warn("Callable scheduled another callable");
            } else {
                final String body = response.getResponseBody();
                final Matcher matcher = PATTERN.matcher(body);
                int count = 0;
                while (matcher.find()) {
                    count++;
                }
                RESULTS_LOG.warn("Currently connected devices : {} out of {}, time elapse: {}", count, deviceCount, stopwatch);
                if (count + 1 != deviceCount) {
                    executor.schedule(new ScaleVerifyCallable(simulator, deviceCount), retryDelay, TimeUnit.SECONDS);
                } else {
                    stopwatch.stop();
                    RESULTS_LOG.warn("All devices connected in {}", stopwatch);
                    simulator.notify();
                }
            }

            return null;
        }
    }

    private static class TimeoutGuard implements Callable {

        @Override
        public Object call() throws Exception {
            RESULTS_LOG.warn("Timeout for scale test reached after: {} ..aborting", stopwatch);
            System.exit(0);
            return null;
        }
    }

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

            public LogOnExceptionCallable(Callable theCallable) {
                super();
                this.theCallable = theCallable;
            }

            @Override
            public Object call() throws Exception {
                try {
                    theCallable.call();
                    return null;
                } catch (Exception e) {
                    // LOG IT HERE!!!
                    System.err.println("error in executing: " + theCallable + ". It will no longer be run!");
                    e.printStackTrace();

                    // and re throw it so that the Executor also gets this error so that it can do what it would
                    // usually do
                    throw new RuntimeException(e);
                }
            }
        }
    }
}

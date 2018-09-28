/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import ch.qos.logback.classic.Level;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressFBWarnings("DM_DEFAULT_ENCODING")
public final class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {
        // hidden on purpose
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @SuppressFBWarnings({"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"})
    public static void main(final String[] args) {
        final TesttoolParameters params = preSetup(args);
        runSimulator(params, new ConfigurationBuilder().from(params).build());
    }

    public static TesttoolParameters preSetup(final String[] args) {
        final TesttoolParameters params = TesttoolParameters.parseArgs(args, TesttoolParameters.getParser());
        params.validate();
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
            .getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(params.debug ? Level.DEBUG : Level.INFO);
        return params;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void runSimulator(final TesttoolParameters params, final Configuration configuration) {
        final NetconfDeviceSimulator netconfDeviceSimulator = new NetconfDeviceSimulator(configuration);
        try {
            LOG.debug("Trying to start netconf test-tool with parameters {}", params);
            final List<Integer> openDevices = netconfDeviceSimulator.start();
            if (openDevices.size() == 0) {
                LOG.error("Failed to start any simulated devices, exiting...");
                System.exit(1);
            }
            //if ODL controller ip is not set NETCONF devices will be started, but not registered at the controller
            if (params.controllerIp != null) {
                final List<Execution> executionThreads = divideDevicesForThreads(openDevices, params);
                final ExecutorService executorService = Executors.newFixedThreadPool(params.threadAmount);
                final Stopwatch time = Stopwatch.createStarted();
                final List<Future<Void>> futures = executorService.invokeAll(executionThreads,
                        params.timeOut, TimeUnit.SECONDS);
                int threadNum = 0;
                for (final Future<Void> future : futures) {
                    threadNum++;
                    if (future.isCancelled()) {
                        LOG.info("{}. thread timed out.", threadNum);
                    } else {
                        try {
                            future.get();
                        } catch (final ExecutionException | InterruptedException e) {
                            LOG.info("{}. thread failed.", threadNum, e);
                        }
                    }
                }
                time.stop();
                LOG.info("Time spent with configuration of devices: {}.", time);
            }
        } catch (final RuntimeException | InterruptedException e) {
            LOG.error("Unhandled exception", e);
            netconfDeviceSimulator.close();
            System.exit(1);
        }

        // Block main thread
        synchronized (netconfDeviceSimulator) {
            try {
                netconfDeviceSimulator.wait();
            } catch (final InterruptedException e) {
                throw new IllegalStateException("Interrupted while waiting", e);
            }
        }
    }

    private static List<Execution> divideDevicesForThreads(final List<Integer> openDevices,
            final TesttoolParameters params) {
        final int devicesPerThread = IntMath.divide(openDevices.size(), params.threadAmount, RoundingMode.UP);
        return Lists.partition(openDevices, devicesPerThread).stream()
                .map(t -> new Execution(t, params))
                .collect(Collectors.toList());
    }
}

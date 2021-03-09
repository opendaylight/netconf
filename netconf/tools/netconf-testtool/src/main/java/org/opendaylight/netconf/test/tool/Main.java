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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressFBWarnings("DM_DEFAULT_ENCODING")
public final class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {

    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @SuppressFBWarnings({"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"})
    public static void main(final String[] args) {
        final TesttoolParameters params = TesttoolParameters.parseArgs(args, TesttoolParameters.getParser());
        params.validate();
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
            .getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(params.debug ? Level.DEBUG : Level.INFO);

        final Configuration configuration = new ConfigurationBuilder().from(params).build();
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
                final PayloadsCreationService payloadsCreationService = new PayloadsCreationService(params);
                final List<List<Execution.DestToPayload>> allThreadsPayloads = payloadsCreationService
                    .getThreadsPayloads(openDevices);
                final List<Execution> executions = new ArrayList<>();
                for (List<Execution.DestToPayload> payloads : allThreadsPayloads) {
                    executions.add(new Execution(params, payloads));
                }
                final ExecutorService executorService = Executors.newFixedThreadPool(params.threadAmount);
                final Stopwatch time = Stopwatch.createStarted();
                List<Future<Void>> futures = executorService.invokeAll(executions, params.timeOut, TimeUnit.SECONDS);
                int threadNum = 0;
                for (Future<Void> future : futures) {
                    threadNum++;
                    if (future.isCancelled()) {
                        LOG.info("{}. thread timed out.",threadNum);
                    } else {
                        try {
                            future.get();
                        } catch (final ExecutionException | InterruptedException e) {
                            LOG.info("{}. thread failed.", threadNum, e);
                        }
                    }
                }
                time.stop();
                LOG.info("Time spent with configuration of devices: {}.",time);
            }
        } catch (RuntimeException | InterruptedException e) {
            LOG.error("Unhandled exception", e);
            netconfDeviceSimulator.close();
            System.exit(1);
        }

        // Block main thread
        synchronized (netconfDeviceSimulator) {
            try {
                netconfDeviceSimulator.wait();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

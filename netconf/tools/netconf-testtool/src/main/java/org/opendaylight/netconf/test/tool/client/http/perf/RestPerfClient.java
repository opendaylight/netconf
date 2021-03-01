/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.client.http.perf;

import com.google.common.base.Stopwatch;
import com.google.common.io.Files;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.opendaylight.netconf.test.tool.client.http.perf.RequestMessageUtils.formPayload;

@SuppressFBWarnings("DM_EXIT")
public final class RestPerfClient {

    private static final Logger LOG = LoggerFactory.getLogger(RestPerfClient.class);

    static int throttle;

    static final class RequestData {

        private final String destination;
        private final String contentString;
        private final int threadId;
        private final int port;
        private final int requests;

        RequestData(final String destination, final String contentString, int threadId, int port, int requests) {
            this.destination = destination;
            this.contentString = contentString;
            this.threadId = threadId;
            this.port = port;
            this.requests = requests;
        }

        public String getDestination() {
            return destination;
        }

        public String getContentString() {
            return contentString;
        }

        public int getThreadId() {
            return threadId;
        }

        public int getPort() {
            return port;
        }

        public int getRequests() {
            return requests;
        }
    }

    private RestPerfClient() {
    }

    public static void main(final String[] args) {

        Parameters parameters = parseArgs(args, Parameters.getParser());
        parameters.validate();
        throttle = parameters.throttle / parameters.threadAmount;

        if (parameters.async && parameters.threadAmount > 1) {
            LOG.info("Throttling per thread: {}", throttle);
        }

        final String editContentString;
        try {
            editContentString = Files.asCharSource(parameters.editContent, StandardCharsets.UTF_8).read();
        } catch (final IOException e) {
            throw new IllegalArgumentException("Cannot read content of " + parameters.editContent, e);
        }

        final int threadAmount = parameters.threadAmount;
        LOG.info("thread amount: {}", threadAmount);
        final int requestsPerThread = parameters.editCount / parameters.threadAmount;
        LOG.info("requestsPerThread: {}", requestsPerThread);
        final int leftoverRequests = parameters.editCount % parameters.threadAmount;
        LOG.info("leftoverRequests: {}", leftoverRequests);

        final ArrayList<RequestData> allThreadsPayloads = new ArrayList<>();

        for (int i = 0; i < threadAmount; i++) {
            int numberOfReq = requestsPerThread;
            if (i == (threadAmount - 1)) {
                numberOfReq += leftoverRequests;
            }
            
            RequestData payload = formPayload(parameters, editContentString, i, numberOfReq);
            allThreadsPayloads.add(payload);
        }

        final ArrayList<PerfClientCallable> callables = new ArrayList<>();
        for (RequestData payload : allThreadsPayloads) {
            callables.add(new PerfClientCallable(parameters, payload));
        }

        final ExecutorService executorService = Executors.newFixedThreadPool(threadAmount);

        LOG.info("Starting performance test");
        boolean allThreadsCompleted = true;
        final Stopwatch started = Stopwatch.createStarted();
        try {
            final List<Future<Void>> futures = executorService.invokeAll(
                    callables, parameters.timeout, TimeUnit.MINUTES);
            for (int i = 0; i < futures.size(); i++) {
                Future<Void> future = futures.get(i);
                if (future.isCancelled()) {
                    allThreadsCompleted = false;
                    LOG.info("{}. thread timed out.", i + 1);
                } else {
                    try {
                        future.get();
                    } catch (final ExecutionException e) {
                        allThreadsCompleted = false;
                        LOG.info("{}. thread failed.", i + 1, e);
                    }
                }
            }
        } catch (final InterruptedException e) {
            allThreadsCompleted = false;
            LOG.warn("Unable to execute requests", e);
        }
        executorService.shutdownNow();
        started.stop();

        LOG.info("FINISHED. Execution time: {}", started);
        // If some threads failed or timed out, skip calculation of requests per second value
        // and do not log it
        if (allThreadsCompleted) {
            LOG.info(
                    "Requests per second: {}", parameters.editCount * 1000.0 / started.elapsed(TimeUnit.MILLISECONDS));
        }
        System.exit(0);
    }

    private static Parameters parseArgs(final String[] args, final ArgumentParser parser) {
        final Parameters opt = new Parameters();
        try {
            parser.parseArgs(args, opt);
            return opt;
        } catch (final ArgumentParserException e) {
            parser.handleError(e);
        }

        System.exit(1);
        return null;
    }

}

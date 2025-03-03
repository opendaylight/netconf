/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Execution implements Callable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(Execution.class);
    private static final String NETCONF_TOPOLOGY_DESTINATION =
            "http://%s:%s/rests/data/network-topology:network-topology/topology=topology-netconf";

    private final HttpClient httpClient;
    private final String destination;
    private final List<Integer> openDevices;
    private final TesttoolParameters params;
    private final Semaphore semaphore;

    private final int throttle;
    private final boolean isAsync;

    Execution(final List<Integer> openDevices, final TesttoolParameters params) {
        httpClient = HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(params.controllerAuthUsername,
                                params.controllerAuthPassword.toCharArray());
                    }
                })
                .build();
        destination = String.format(Locale.ROOT, NETCONF_TOPOLOGY_DESTINATION,
                params.controllerIp, params.controllerPort);
        this.openDevices = openDevices;
        this.params = params;

        throttle = params.throttle / params.threadAmount;
        isAsync = params.async;

        if (params.async && params.threadAmount > 1) {
            LOG.info("Throttling per thread: {}", throttle);
        }
        semaphore = new Semaphore(throttle);
    }

    @Override
    public Void call() {
        final List<HttpRequest> requests = prepareRequests();
        if (isAsync) {
            sendAsync(requests);
        } else {
            sendSync(requests);
        }
        return null;
    }

    private List<HttpRequest> prepareRequests() {
        final List<List<Integer>> batches = Lists.partition(openDevices, params.generateConfigBatchSize);
        return batches.stream()
                .map(b -> PayloadCreator.createStringPayload(b, params))
                .map(this::prepareRequest)
                .collect(Collectors.toList());
    }

    private void sendAsync(final List<HttpRequest> requests) {
        LOG.info("Begin sending async requests");
        for (final HttpRequest request : requests) {
            try {
                semaphore.acquire();
            } catch (final InterruptedException e) {
                LOG.warn("Semaphore acquire interrupted");
            }
            httpClient.sendAsync(request, BodyHandlers.ofString()).whenComplete((response, error) -> {
                if (response.statusCode() != 200) {
                    LOG.warn("Unexpected status code: {} for request to uri: {} with body: {}",
                            response.statusCode(), request.uri(), response.body());
                }
                semaphore.release();
            });
        }
        LOG.info("Requests sent, waiting for responses");
        try {
            semaphore.acquire(throttle);
        } catch (final InterruptedException e) {
            LOG.warn("Semaphore acquire interrupted");
        }
        LOG.info("Responses received, ending...");
    }

    private void sendSync(final List<HttpRequest> requests) {
        LOG.info("Begin sending sync requests");
        for (final HttpRequest request : requests) {
            try {
                final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    LOG.warn("Unexpected status code: {} for request to uri: {} with body: {}",
                            response.statusCode(), request.uri(), response.body());
                }
            } catch (final InterruptedException | IOException e) {
                LOG.error("Failed to execute request: {}", request, e);
                throw new IllegalStateException("Failed to execute request", e);
            }
        }
        LOG.info("End sending sync requests");
    }

    private HttpRequest prepareRequest(final String payload) {
        LOG.info("Creating request to: {} with payload: {}", destination, payload);
        return HttpRequest.newBuilder(URI.create(destination))
                .method("PATCH", BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();
    }
}

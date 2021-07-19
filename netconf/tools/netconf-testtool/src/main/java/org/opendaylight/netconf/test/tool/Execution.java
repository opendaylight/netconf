/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

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
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Execution implements Callable<Void> {

    private final ArrayList<HttpRequest> payloads;
    private final HttpClient httpClient;
    private static final Logger LOG = LoggerFactory.getLogger(Execution.class);
    private final boolean invokeAsync;
    private final Semaphore semaphore;
    private final int throttle;

    static final class DestToPayload {

        private final String destination;
        private final String payload;

        DestToPayload(final String destination, final String payload) {
            this.destination = destination;
            this.payload = payload;
        }

        public String getDestination() {
            return destination;
        }

        public String getPayload() {
            return payload;
        }
    }

    public Execution(final TesttoolParameters params, final ArrayList<DestToPayload> payloads) {
        this.invokeAsync = params.async;
        this.throttle = params.throttle / params.threadAmount;

        if (params.async && params.threadAmount > 1) {
            LOG.info("Throttling per thread: {}", this.throttle);
        }
        this.semaphore = new Semaphore(this.throttle);

        this.httpClient = HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(params.controllerAuthUsername,
                                params.controllerAuthPassword.toCharArray());
                    }
                })
                .build();
        this.payloads = new ArrayList<>();
        for (DestToPayload payload : payloads) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(payload.getDestination()))
                    .POST(BodyPublishers.ofString(payload.getPayload(), StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();

            this.payloads.add(request);
        }
    }

    private void invokeSync() {
        LOG.info("Begin sending sync requests");
        for (HttpRequest request : payloads) {
            try {
                HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

                if (response.statusCode() != 200 && response.statusCode() != 204) {
                    if (response.statusCode() == 409) {
                        LOG.warn("Request failed, status code: {} - one or more of the devices"
                                + " is already configured, skipping the whole batch", response.statusCode());
                    } else {
                        LOG.warn("Status code: {}", response.statusCode());
                        LOG.warn("url: {}", request.uri());
                        LOG.warn("body: {}", response.body());
                    }
                }
            } catch (InterruptedException | IOException e) {
                LOG.warn("Failed to execute request", e);
            }
        }
        LOG.info("End sending sync requests");
    }

    private void invokeAsync() {
        LOG.info("Begin sending async requests");

        for (final HttpRequest request : payloads) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                LOG.warn("Semaphore acquire interrupted");
            }
            httpClient.sendAsync(request, BodyHandlers.ofString()).whenComplete((response, error) -> {
                if (response.statusCode() != 200 && response.statusCode() != 204) {
                    if (response.statusCode() == 409) {
                        LOG.warn("Request failed, status code: {} - one or more of the devices"
                                + " is already configured, skipping the whole batch", response.statusCode());
                    } else {
                        LOG.warn("Request failed, status code: {}", response.statusCode());
                        LOG.warn("request: {}", request.toString());
                    }
                }
                semaphore.release();
            });
        }
        LOG.info("Requests sent, waiting for responses");

        try {
            semaphore.acquire(this.throttle);
        } catch (InterruptedException e) {
            LOG.warn("Semaphore acquire interrupted");
        }

        LOG.info("Responses received, ending...");
    }

    @Override
    public Void call() {
        if (invokeAsync) {
            this.invokeAsync();
        } else {
            this.invokeSync();
        }
        return null;
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Realm;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Execution implements Callable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(Execution.class);

    private final ArrayList<Request> payloads;
    private final AsyncHttpClient asyncHttpClient;
    private final Semaphore semaphore;

    private final int throttle;
    private final boolean invokeAsync;

    static final class DestToPayload {
        private final String destination;
        private final String payload;

        DestToPayload(final String destination, final String payload) {
            LOG.info("Destination: {}", destination);
            LOG.info("Payload: {}", payload);
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

    Execution(final TesttoolParameters params, final List<DestToPayload> payloads) {
        this.invokeAsync = params.isAsync();
        this.throttle = params.getThrottle() / params.getThreadAmount();

        if (params.isAsync() && params.getThreadAmount() > 1) {
            LOG.info("Throttling per thread: {}", this.throttle);
        }
        this.semaphore = new Semaphore(this.throttle);

        this.asyncHttpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
                .setConnectTimeout(Integer.MAX_VALUE)
                .setRequestTimeout(Integer.MAX_VALUE)
                .setAllowPoolingConnections(true)
                .build());

        this.payloads = new ArrayList<>();
        for (final DestToPayload payload : payloads) {
            final AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient
                    .preparePatch(payload.getDestination())
                    .addHeader("Content-Type", "application/yang-data+json")
                    .addHeader("Accept", "application/json")
                    .setBody(payload.getPayload())
                    .setRequestTimeout(Integer.MAX_VALUE);

            requestBuilder.setRealm(new Realm.RealmBuilder()
                    .setScheme(Realm.AuthScheme.BASIC)
                    .setPrincipal(params.getControllerAuthUsername())
                    .setPassword(params.getControllerAuthPassword())
                    .setMethodName(HttpMethod.PATCH.getName())
                    .setUsePreemptiveAuth(true)
                    .build());

            this.payloads.add(requestBuilder.build());
        }
    }

    private void invokeSync() {
        LOG.info("Begin sending sync requests");
        for (final Request request : payloads) {
            try {
                final Response response = asyncHttpClient.executeRequest(request).get();
                if (response.getStatusCode() != 200) {
                    LOG.warn("Unexpected status code: {} for request to url: {} with response: {}",
                            response.getStatusCode(), request.getUrl(), response.getResponseBody());
                }
            } catch (final InterruptedException | ExecutionException | IOException e) {
                LOG.error("Failed to execute request: {}", request, e);
                throw new RuntimeException("Failed to execute request", e);
            }
        }
        LOG.info("End sending sync requests");
    }

    private void invokeAsync() {
        LOG.info("Begin sending async requests");

        for (final Request request : payloads) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                LOG.warn("Semaphore acquire interrupted");
            }
            asyncHttpClient.executeRequest(request, new AsyncCompletionHandler<Response>() {
                @Override
                public STATE onStatusReceived(final HttpResponseStatus status) throws Exception {
                    super.onStatusReceived(status);
                    if (status.getStatusCode() != 200) {
                        LOG.warn("Unexpected status code: {} for request to url: {}",
                                status.getStatusCode(), request.getUrl());
                    }
                    return STATE.CONTINUE;
                }

                @Override
                public Response onCompleted(final Response response) {
                    semaphore.release();
                    return response;
                }
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

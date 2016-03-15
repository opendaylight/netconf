/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.netconf.test.tool;

import com.ning.http.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

public class Execution implements Callable<Void> {

    private final ArrayList<Request> payloads;
    private final AsyncHttpClient asyncHttpClient;
    private static final Logger LOG = LoggerFactory.getLogger(Execution.class);
    private final boolean invokeAsync;
    private final Semaphore semaphore;
    private final int throttle;

    static final class DestToPayload {

        private final String destination;
        private final String payload;

        public DestToPayload(String destination, String payload) {
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

    public Execution(TesttoolParameters params, ArrayList<DestToPayload> payloads) {
        this.invokeAsync = params.async;
        this.throttle = params.throttle / params.threadAmount;

        if (params.async && params.threadAmount > 1) {
            LOG.info("Throttling per thread: {}", this.throttle);
        }
        this.semaphore = new Semaphore(this.throttle);

        this.asyncHttpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
                .setConnectTimeout(Integer.MAX_VALUE)
                .setRequestTimeout(Integer.MAX_VALUE)
                .setAllowPoolingConnections(true)
                .build());

        this.payloads = new ArrayList<>();
        for (DestToPayload payload : payloads) {
            AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient.preparePut(payload.getDestination())
                    .addHeader("Content-Type", "application/xml")
                    .addHeader("Accept", "application/xml")
                    .setBody(payload.getPayload())
                    .setRequestTimeout(Integer.MAX_VALUE);

            if (params.auth != null) {
                requestBuilder.setRealm(new Realm.RealmBuilder()
                        .setScheme(Realm.AuthScheme.BASIC)
                        .setPrincipal(params.auth.get(0))
                        .setPassword(params.auth.get(1))
                        .setMethodName("PUT")
                        .setUsePreemptiveAuth(true)
                        .build());
            }
            this.payloads.add(requestBuilder.build());
        }
    }

    private void invokeSync() {
        LOG.info("Begin sending sync requests");
        for (Request request : payloads) {
            try {
                Response response = asyncHttpClient.executeRequest(request).get();
                if (response.getStatusCode() != 200 && response.getStatusCode() != 204) {
                    LOG.warn("Status code: {}", response.getStatusCode());
                    LOG.warn("url: {}", request.getUrl());
                    LOG.warn(response.getResponseBody());
                }
            } catch (InterruptedException | ExecutionException | IOException e) {
                LOG.warn(e.toString());
            }
        }
        LOG.info("End sending sync requests");
    }

    private void invokeAsync() {
        final ArrayList<ListenableFuture<Response>> futures = new ArrayList<>();
        LOG.info("Begin sending async requests");

        for (final Request request : payloads) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                LOG.warn("Semaphore acquire interrupted");
            }
            futures.add(asyncHttpClient.executeRequest(request, new AsyncCompletionHandler<Response>() {
                @Override
                public STATE onStatusReceived(HttpResponseStatus status) throws Exception {
                    super.onStatusReceived(status);
                    if (status.getStatusCode() != 200 && status.getStatusCode() != 204) {
                        LOG.warn("Request failed, status code: {}", status.getStatusCode() + status.getStatusText());
                        LOG.warn("request: {}", request.toString());
                    }
                    return STATE.CONTINUE;
                }

                @Override
                public Response onCompleted(Response response) throws Exception {
                    semaphore.release();
                    return response;
                }
            }));
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
    public Void call() throws Exception {
        if (invokeAsync) {
            this.invokeAsync();
        } else {
            this.invokeSync();
        }
        return null;
    }
}

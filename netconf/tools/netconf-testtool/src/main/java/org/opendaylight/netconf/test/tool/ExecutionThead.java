/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import com.google.common.collect.Lists;
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
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ExecutionThead implements Callable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionThead.class);
    public static final String NETCONF_TOPOLOGY_DESTINATION =
            "http://%s:%s/rests/data/network-topology:network-topology/topology=topology-netconf";

    private final List<Integer> openDevices;
    private final TesttoolParameters params;
    private final AsyncHttpClient asyncHttpClient;
    private final String destination;
    private final Semaphore semaphore;

    private final int throttle;

    ExecutionThead(final List<Integer> openDevices, final TesttoolParameters params) {
        this.openDevices = openDevices;
        this.params = params;
        this.asyncHttpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
                .setConnectTimeout(Integer.MAX_VALUE).setRequestTimeout(Integer.MAX_VALUE)
                .setAllowPoolingConnections(true).build());
        destination = String.format(Locale.ROOT, NETCONF_TOPOLOGY_DESTINATION, params.getControllerIp(),
                params.getControllerPort());

        throttle = params.getThrottle() / params.getThreadAmount();
        if (params.isAsync() && params.getThreadAmount() > 1) {
            LOG.info("Throttling per thread: {}", throttle);
        }
        semaphore = new Semaphore(this.throttle);
    }

    private List<Request> createRequests() {
        final List<Request> requests = new ArrayList<>();
        final List<List<Integer>> batches = Lists.partition(openDevices, params.getGenerateConfigBatchSize());
        for (int i = 0; i < batches.size(); i++) {
            final String payload = PayloadCreator.createStringPayload(i, batches.get(i), params);
            requests.add(createRequest(payload));
        }
        return requests;
    }

    private Request createRequest(final String payload) {
        LOG.info("Creating request to: {} with payload: {}", destination, payload);
        final AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient
                .preparePatch(destination)
                .addHeader("Content-Type", "application/yang-data+json")
                .addHeader("Accept", "application/json")
                .setBody(payload)
                .setRequestTimeout(Integer.MAX_VALUE);

        return requestBuilder.setRealm(new Realm.RealmBuilder()
                .setScheme(Realm.AuthScheme.BASIC)
                .setPrincipal(params.getControllerAuthUsername())
                .setPassword(params.getControllerAuthPassword())
                .setMethodName(HttpMethod.PATCH.getName())
                .setUsePreemptiveAuth(true)
                .build()).build();
    }

    private void invokeSync() {
        LOG.info("Begin sending sync requests");
        for (final Request request : createRequests()) {
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
        for (final Request request : createRequests()) {
            try {
                semaphore.acquire();
            } catch (final InterruptedException e) {
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
        } catch (final InterruptedException e) {
            LOG.warn("Semaphore acquire interrupted");
        }

        LOG.info("Responses received, ending...");
    }

    @Override
    public Void call() {
        if (params.isAsync()) {
            this.invokeAsync();
        } else {
            this.invokeSync();
        }
        return null;
    }
}

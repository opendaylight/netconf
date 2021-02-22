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
    private static final String NETCONF_TOPOLOGY_DESTINATION =
            "http://%s:%s/rests/data/network-topology:network-topology/topology=topology-netconf";

    private final AsyncHttpClient httpClient;
    private final String destination;
    private final List<Integer> openDevices;
    private final TesttoolParameters params;
    private final Semaphore semaphore;

    private final int throttle;
    private final boolean isAsync;

    ExecutionThead(final List<Integer> openDevices, final TesttoolParameters params) {
        httpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
                .setConnectTimeout(Integer.MAX_VALUE).setRequestTimeout(Integer.MAX_VALUE)
                .setAllowPoolingConnections(true).build());
        destination = String.format(Locale.ROOT, NETCONF_TOPOLOGY_DESTINATION,
                params.getControllerIp(), params.getControllerPort());
        this.openDevices = openDevices;
        this.params = params;

        throttle = params.getThrottle() / params.getThreadAmount();
        isAsync = params.isAsync();

        if (params.isAsync() && params.getThreadAmount() > 1) {
            LOG.info("Throttling per thread: {}", throttle);
        }
        semaphore = new Semaphore(throttle);
    }

    @Override
    public Void call() {
        final List<Request> requests = prepareRequests();
        if (isAsync) {
            this.sendAsync(requests);
        } else {
            this.sendSync(requests);
        }
        return null;
    }

    private List<Request> prepareRequests() {
        final List<Request> requests = new ArrayList<>();
        final List<List<Integer>> batches = Lists.partition(openDevices, params.getGenerateConfigBatchSize());
        for (final List<Integer> batch : batches) {
            final String payload = PayloadCreator.createStringPayload(batch, params);
            requests.add(prepareRequest(payload));
        }
        return requests;
    }

    private void sendAsync(final List<Request> requests) {
        LOG.info("Begin sending async requests");
        for (final Request request : requests) {
            try {
                semaphore.acquire();
            } catch (final InterruptedException e) {
                LOG.warn("Semaphore acquire interrupted");
            }
            httpClient.executeRequest(request, new AsyncCompletionHandler<Response>() {
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

    private void sendSync(final List<Request> requests) {
        LOG.info("Begin sending sync requests");
        for (final Request request : requests) {
            try {
                final Response response = httpClient.executeRequest(request).get();
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

    private Request prepareRequest(final String payload) {
        LOG.info("Creating request to: {} with payload: {}", destination, payload);
        final AsyncHttpClient.BoundRequestBuilder requestBuilder = httpClient
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
}

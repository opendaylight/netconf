/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.client.http.perf;

import static org.opendaylight.netconf.test.tool.client.http.perf.RequestMessageUtils.formRequest;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.Semaphore;
import org.opendaylight.netconf.test.tool.client.stress.ExecutionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncExecutionStrategy implements ExecutionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncExecutionStrategy.class);

    private final HttpClient httpClient;
    private final Parameters params;
    private final Semaphore semaphore;
    RestPerfClient.RequestData payloads;

    AsyncExecutionStrategy(final Parameters params, final HttpClient httpClient,
            final RestPerfClient.RequestData payloads) {
        this.params = params;
        this.httpClient = httpClient;
        this.payloads = payloads;
        this.semaphore = new Semaphore(RestPerfClient.throttle);
    }

    @Override
    public void invoke() {
        LOG.info("Begin sending async requests");

        for (int i = 0; i < payloads.getRequests(); i++) {
            final String message = RequestMessageUtils.prepareMessage(payloads.getThreadId(), i,
                    payloads.getContentString(), payloads.getPort());
            final String url = payloads.getDestination();
            final HttpRequest request = formRequest(url, message);
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                LOG.warn("Semaphore acquire interrupted");
            }
            httpClient.sendAsync(request, BodyHandlers.ofString()).whenComplete((response, error) -> {
                switch (response.statusCode()) {
                    case 200:
                    case 204:
                        break;
                    default:
                        LOG.warn("Request failed, status code: {}", response.statusCode());
                        LOG.warn("request: {}", request);
                }
                semaphore.release();
            });
        }
        LOG.info("Requests sent, waiting for responses");

        try {
            semaphore.acquire(RestPerfClient.throttle);
        } catch (InterruptedException e) {
            LOG.warn("Semaphore acquire interrupted");
        }

        LOG.info("Responses received, ending...");
    }
}

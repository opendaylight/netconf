/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.client.http.perf;

import static org.opendaylight.netconf.test.tool.client.http.perf.RequestMessageUtils.formRequest;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import java.util.concurrent.Semaphore;
import org.opendaylight.netconf.test.tool.client.stress.ExecutionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncExecutionStrategy implements ExecutionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncExecutionStrategy.class);

    private final Parameters params;
    private final AsyncHttpClient asyncHttpClient;
    private final Semaphore semaphore;
    RestPerfClient.RequestData payloads;

    AsyncExecutionStrategy(final Parameters params, final AsyncHttpClient asyncHttpClient,
                           final RestPerfClient.RequestData payloads) {
        this.params = params;
        this.asyncHttpClient = asyncHttpClient;
        this.payloads = payloads;
        this.semaphore = new Semaphore(RestPerfClient.throttle);
    }

    @Override
    public void invoke() {
        LOG.info("Begin sending async requests");

        for (int i = 0; i < payloads.getRequests(); i++) {
            String message = RequestMessageUtils.prepareMessage(payloads.getThreadId(), i,
                    payloads.getContentString(), payloads.getPort());
            Request request = formRequest(asyncHttpClient, payloads.getDestination(), params, message);
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                LOG.warn("Semaphore acquire interrupted");
            }
            asyncHttpClient.executeRequest(request, new AsyncCompletionHandler<Response>() {
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
                public Response onCompleted(Response response) {
                    semaphore.release();
                    return response;
                }
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

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.client.http.perf;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.opendaylight.netconf.test.tool.client.stress.ExecutionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.opendaylight.netconf.test.tool.client.http.perf.RequestMessageUtils.formRequest;

public class SyncExecutionStrategy implements ExecutionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(SyncExecutionStrategy.class);

    private final Parameters params;
    private final RestPerfClient.RequestData payloads;
    private final AsyncHttpClient asyncHttpClient;

    SyncExecutionStrategy(final Parameters params, final AsyncHttpClient asyncHttpClient,
                          final RestPerfClient.RequestData payloads) {
        this.params = params;
        this.asyncHttpClient = asyncHttpClient;
        this.payloads = payloads;
    }

    @Override
    public void invoke() {

        LOG.info("Begin sending sync requests");
        for (int i = 0; i < payloads.getRequests(); i++) {
            String message = RequestMessageUtils.prepareMessage(payloads.getThreadId(), i,
                    payloads.getContentString(), payloads.getPort());
            Request request = formRequest(asyncHttpClient, payloads.getDestination(), params, message);
            try {
                Response response = asyncHttpClient.executeRequest(request).get();
                if (response.getStatusCode() != 200 && response.getStatusCode() != 204) {
                    LOG.warn("Status code: {}", response.getStatusCode());
                    LOG.warn("url: {}", request.getUrl());
                    LOG.warn("body: {}", response.getResponseBody());
                }
            } catch (InterruptedException | ExecutionException | IOException e) {
                LOG.warn("Failed to execute request", e);
            }
        }
        LOG.info("End sending sync requests");

    }
}

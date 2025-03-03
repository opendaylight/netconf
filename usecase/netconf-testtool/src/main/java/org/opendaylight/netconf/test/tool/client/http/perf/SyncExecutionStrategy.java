/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.client.http.perf;

import static org.opendaylight.netconf.test.tool.client.http.perf.RequestMessageUtils.formRequest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import org.opendaylight.netconf.test.tool.client.stress.ExecutionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncExecutionStrategy implements ExecutionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(SyncExecutionStrategy.class);
    private static final List<Integer> ACCEPTED_CODES = List.of(200, 201, 204);

    private final HttpClient httpClient;
    private final RestPerfClient.RequestData payloads;

    SyncExecutionStrategy(final HttpClient httpClient, final RestPerfClient.RequestData payloads) {
        this.httpClient = httpClient;
        this.payloads = payloads;
    }

    @Override
    public void invoke() {

        LOG.info("Begin sending sync requests");
        for (int i = 0; i < payloads.getRequests(); i++) {
            final String message = RequestMessageUtils.prepareMessage(payloads.getThreadId(), i,
                    payloads.getContentString(), payloads.getPort());
            final HttpResponse<String> response;
            try {
                response = httpClient.send(formRequest(payloads.getDestination(), message), BodyHandlers.ofString());
            } catch (InterruptedException | IOException e) {
                LOG.warn("Failed to execute request", e);
                return;
            }

            if (!ACCEPTED_CODES.contains(response.statusCode())) {
                LOG.warn("Status code: {}", response.statusCode());
                LOG.warn("url: {}", response.uri());
                LOG.warn("body: {}", response.body());
            }
        }
        LOG.info("End sending sync requests");
    }
}

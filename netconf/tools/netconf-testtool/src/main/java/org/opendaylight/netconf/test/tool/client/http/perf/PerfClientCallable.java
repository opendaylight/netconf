/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.client.http.perf;

import java.io.IOException;
import java.util.concurrent.Callable;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.opendaylight.netconf.test.tool.client.http.perf.RestPerfClient.RequestData;
import org.opendaylight.netconf.test.tool.client.stress.ExecutionStrategy;

public class PerfClientCallable implements Callable<Void> {
    private final Parameters params;
    private final AsyncHttpClient asyncHttpClient;
    private final ExecutionStrategy executionStrategy;
    private final RequestData payloads;

    public PerfClientCallable(final Parameters params, final RequestData payloads) {
        this.params = params;
        this.payloads = payloads;
        this.asyncHttpClient = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(Integer.MAX_VALUE)
                .setRequestTimeout(Integer.MAX_VALUE)
                .build());
        executionStrategy = getExecutionStrategy();
    }

    private ExecutionStrategy getExecutionStrategy() {
        return params.async
                ? new AsyncExecutionStrategy(params, asyncHttpClient, payloads)
                : new SyncExecutionStrategy(params, asyncHttpClient, payloads);
    }

    @Override
    public Void call() throws IOException {
        executionStrategy.invoke();
        asyncHttpClient.close();
        return null;
    }
}

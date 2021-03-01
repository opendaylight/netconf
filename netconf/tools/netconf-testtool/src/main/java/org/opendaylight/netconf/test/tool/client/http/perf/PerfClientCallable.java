/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.client.http.perf;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import java.util.concurrent.Callable;
import org.opendaylight.netconf.test.tool.client.http.perf.RestPerfClient.RequestData;
import org.opendaylight.netconf.test.tool.client.stress.ExecutionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerfClientCallable implements Callable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(PerfClientCallable.class);

    private final Parameters params;
    private final AsyncHttpClient asyncHttpClient;
    private ExecutionStrategy executionStrategy;
    private RequestData payloads;

    public PerfClientCallable(Parameters params, RequestData payloads) {
        this.params = params;
        this.payloads = payloads;
        this.asyncHttpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
                .setConnectTimeout(Integer.MAX_VALUE)
                .setRequestTimeout(Integer.MAX_VALUE)
                .setAllowPoolingConnections(true)
                .build());
        executionStrategy = getExecutionStrategy();
    }

    private ExecutionStrategy getExecutionStrategy() {
        return params.async
                ? new AsyncExecutionStrategy(params, asyncHttpClient, payloads)
                : new SyncExecutionStrategy(params, asyncHttpClient, payloads);
    }

    @Override
    public Void call() {
        executionStrategy.invoke();
        asyncHttpClient.closeAsynchronously();
        return null;
    }
}

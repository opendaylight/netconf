/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.client.http.perf;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.util.concurrent.Callable;
import org.opendaylight.netconf.test.tool.client.http.perf.RestPerfClient.RequestData;
import org.opendaylight.netconf.test.tool.client.stress.ExecutionStrategy;

public class PerfClientCallable implements Callable<Void> {
    private final ExecutionStrategy executionStrategy;

    public PerfClientCallable(final Parameters params, final RequestData payloads) {
        final Builder builder = HttpClient.newBuilder();
        if (params.auth != null) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(params.auth.get(0), params.auth.get(1).toCharArray());
                }
            });
        }

        this.executionStrategy = params.async
            ? new AsyncExecutionStrategy(params, builder.build(), payloads)
            : new SyncExecutionStrategy(builder.build(), payloads);
    }

    @Override
    public Void call() {
        executionStrategy.invoke();
        return null;
    }
}

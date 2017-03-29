/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import static com.ning.http.client.AsyncHttpClientConfigDefaults.defaultConnectTimeout;
import static com.ning.http.client.AsyncHttpClientConfigDefaults.defaultFollowRedirect;
import static com.ning.http.client.AsyncHttpClientConfigDefaults.defaultPooledConnectionIdleTimeout;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.providers.netty.NettyAsyncHttpProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NettyHttpClientProviderTest {


    private NettyHttpClientProvider provider;

    @Before
    public void setUp() {
        final TrustStore trustStore = new TrustStore(getClass().getResource("/store/cacerts").toExternalForm(), "changeit", "JKS", "CLASSPATH");
        provider = new NettyHttpClientProvider(trustStore);
    }

    @Test
    public void testCreateHttpClient() throws Exception {
        final AsyncHttpClient httpClient = provider.createHttpClient("admin", "admin", defaultPooledConnectionIdleTimeout(), 4000, 4000, true);
        Assert.assertTrue(httpClient.getProvider() instanceof NettyAsyncHttpProvider);
    }

    @Test
    public void testCreateSseClient() throws Exception {
        final AsyncHttpClient httpClient = provider.createHttpClient("admin", "admin", -1, -1, defaultConnectTimeout(), defaultFollowRedirect());
        Assert.assertTrue(httpClient.getProvider() instanceof NettyAsyncHttpProvider);
    }

}
/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.Promise;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.protocol.framework.SessionListenerFactory;
import org.opendaylight.protocol.framework.SessionNegotiator;

public class TlsClientChannelInitializerTest {
    @Mock
    private SslHandlerFactory sslHandlerFactory;
    @Mock
    private NetconfClientSessionNegotiatorFactory negotiatorFactory;
    @Mock
    private NetconfClientSessionListener sessionListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInitialize() throws Exception {
        SessionNegotiator<?> sessionNegotiator = mock(SessionNegotiator.class);
        doReturn(sessionNegotiator).when(negotiatorFactory).getSessionNegotiator(any(SessionListenerFactory.class),
                any(Channel.class), any(Promise.class));
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        doReturn(pipeline).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));
        Channel channel = mock(Channel.class);
        doReturn(pipeline).when(channel).pipeline();

        doReturn(pipeline).when(pipeline).addFirst(anyString(), any(ChannelHandler.class));
        doReturn(pipeline).when(pipeline).addLast(anyString(), any(ChannelHandler.class));

        Promise<NetconfClientSession> promise = mock(Promise.class);

        TlsClientChannelInitializer initializer = new TlsClientChannelInitializer(sslHandlerFactory,
                negotiatorFactory, sessionListener);
        initializer.initialize(channel, promise);
        verify(pipeline, times(1)).addFirst(anyString(), any(ChannelHandler.class));
    }
}

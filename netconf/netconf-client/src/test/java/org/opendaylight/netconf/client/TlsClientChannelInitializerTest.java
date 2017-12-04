/*
 * Copyright (c) 2017 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Promise;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.netconf.util.NetconfSslContextFactory;
import org.opendaylight.netconf.util.osgi.NetconfConfigUtil;
import org.opendaylight.netconf.util.osgi.NetconfConfiguration;
import org.opendaylight.protocol.framework.SessionListenerFactory;
import org.opendaylight.protocol.framework.SessionNegotiator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({FrameworkUtil.class, NetconfConfigUtil.class, NetconfSslContextFactory.class})
public class TlsClientChannelInitializerTest {
    @Test
    public void test() throws Exception {
        PowerMockito.mockStatic(FrameworkUtil.class);

        BundleContext bundleContext = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);

        given(FrameworkUtil.getBundle(any(Class.class))).willReturn(bundle);
        given(bundle.getBundleContext()).willReturn(bundleContext);

        PowerMockito.mockStatic(NetconfConfigUtil.class);
        NetconfConfiguration netconfConfiguration = mock(NetconfConfiguration.class);
        given(NetconfConfigUtil.getNetconfConfigurationService(bundleContext)).willReturn(netconfConfiguration);

        PowerMockito.mockStatic(NetconfSslContextFactory.class);
        SslHandler sslHandler = mock(SslHandler.class);
        given(NetconfSslContextFactory.getClientSslHandler(netconfConfiguration)).willReturn(sslHandler);

        Channel channel = mock(Channel.class);
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        doReturn(pipeline).when(channel).pipeline();
        doReturn(pipeline).when(pipeline).addFirst(any(ChannelHandler.class));
        doReturn(pipeline).when(pipeline).addLast(anyString(), any(ChannelHandler.class));
        doReturn(pipeline).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));

        NetconfClientSessionNegotiatorFactory negotiatorFactory = mock(NetconfClientSessionNegotiatorFactory.class);
        SessionNegotiator<?> sessionNegotiator = mock(SessionNegotiator.class);
        doReturn(sessionNegotiator).when(negotiatorFactory).getSessionNegotiator(any(SessionListenerFactory.class),
                any(Channel.class), any(Promise.class));

        Promise<NetconfClientSession> promise = mock(Promise.class);
        NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        TlsClientChannelInitializer initializer = new TlsClientChannelInitializer(negotiatorFactory, sessionListener);
        initializer.initialize(channel, promise);
        verify(pipeline, times(1)).addFirst(any(ChannelHandler.class));
        verify(pipeline, times(1)).addAfter(anyString(), anyString(), any(ChannelHandler.class));
        verify(pipeline, times(4)).addLast(anyString(), any(ChannelHandler.class));
    }
}

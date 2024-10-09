/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.transport.http.ConfigUtils.serverTransportTcp;
import static org.opendaylight.netconf.transport.http.TestUtils.freePort;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;

@ExtendWith(MockitoExtension.class)
public class HTTPServerTest {
    @Mock
    private TransportChannelListener<TransportChannel> serverTransportListener;
    @Mock
    private HttpServerStackGrouping serverConfig;
    @Mock
    private TransportChannel transportChannel;
    @Mock
    private Channel channel;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private ChannelFuture future;

    @Test
    void onChannelCloseTest() throws Exception {
        // setup
        final var featureListener = ArgumentCaptor.forClass(GenericFutureListener.class);
        final var localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        final var localPort = freePort();
        doReturn(serverTransportTcp(localAddress, localPort)).when(serverConfig).getTransport();
        doReturn(channel).when(transportChannel).channel();
        doReturn(pipeline).when(channel).pipeline();
        doReturn(future).when(channel).closeFuture();
        doReturn(pipeline).when(pipeline).addLast(any());
        final var bootstrapFactory = new BootstrapFactory("IntegrationTest", 0);

        final var server = HTTPServer.listen(serverTransportListener, bootstrapFactory.newServerBootstrap(),
            serverConfig, null).get(2, TimeUnit.SECONDS);
        server.onUnderlayChannelEstablished(transportChannel);
        // verify listener was added and capture it
        verify(future).addListener(featureListener.capture());
        // invoke on channel close listener
        featureListener.getValue().operationComplete(null);
        // verify onTransportChannelClosed() vas called
        verify(serverTransportListener).onTransportChannelClosed(any());
    }
}

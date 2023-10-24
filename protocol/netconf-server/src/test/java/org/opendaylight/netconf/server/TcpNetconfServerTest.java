/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;

@ExtendWith(MockitoExtension.class)
class TcpNetconfServerTest extends AbstractNetconfServerTest {
    @Test
    void tcpServer() throws Exception {
        final var server = TCPServer.listen(initializer, bootstrapFactory.newServerBootstrap(), tcpServerParams)
            .get(1, TimeUnit.SECONDS);
        try {
            final var client = TCPClient.connect(clientListener, bootstrapFactory.newBootstrap(), tcpClientParams)
                .get(1, TimeUnit.SECONDS);
            try {
                verify(negotiatorFactory, timeout(1000L)).getSessionNegotiator(any(Channel.class), any());
                verify(clientListener, timeout(1000L)).onTransportChannelEstablished(any(TransportChannel.class));
            } finally {
                client.shutdown().get(1, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(1, TimeUnit.SECONDS);
        }
    }
}

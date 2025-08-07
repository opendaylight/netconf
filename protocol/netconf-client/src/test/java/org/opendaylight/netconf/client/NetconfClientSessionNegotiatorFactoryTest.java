/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;

@ExtendWith(MockitoExtension.class)
class NetconfClientSessionNegotiatorFactoryTest {
    @Mock
    private NetconfClientSessionListener sessionListener;
    @Mock
    private Channel channel;
    @Mock
    private Promise<NetconfClientSession> promise;

    @Test
    void testGetSessionNegotiator() throws Exception {
        final var timer = new DefaultNetconfTimer();
        final var negotiatorFactory = new NetconfClientSessionNegotiatorFactory(timer, Optional.empty(), 200L);

        final var sessionNegotiator = negotiatorFactory.getSessionNegotiator(sessionListener, channel, promise);
        assertNotNull(sessionNegotiator);
    }
}

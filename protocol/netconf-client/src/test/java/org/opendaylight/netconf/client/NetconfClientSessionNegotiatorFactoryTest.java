/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;

class NetconfClientSessionNegotiatorFactoryTest {
    @Test
    void testGetSessionNegotiator() throws Exception {
        NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        final var timer = new DefaultNetconfTimer();

        Channel channel = mock(Channel.class);
        Promise<NetconfClientSession> promise = mock(Promise.class);
        NetconfClientSessionNegotiatorFactory negotiatorFactory = new NetconfClientSessionNegotiatorFactory(timer,
                Optional.empty(), 200L);

        NetconfClientSessionNegotiator sessionNegotiator = negotiatorFactory.getSessionNegotiator(sessionListener,
            channel, promise);
        assertNotNull(sessionNegotiator);
    }
}

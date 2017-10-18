/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.protocol.framework.SessionListenerFactory;

final class ReverseSshChannelInitializer extends AbstractChannelInitializer<NetconfClientSession>
        implements SessionListenerFactory<NetconfClientSessionListener> {

    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    private ReverseSshChannelInitializer(NetconfClientSessionNegotiatorFactory negotiatorFactory,
                                         NetconfClientSessionListener sessionListener) {
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
    }

    public static ReverseSshChannelInitializer create(NetconfClientSessionNegotiatorFactory negotiatorFactory,
                                                      NetconfClientSessionListener listener) {
        return new ReverseSshChannelInitializer(negotiatorFactory, listener);
    }

    @Override
    public NetconfClientSessionListener getSessionListener() {
        return sessionListener;
    }

    @Override
    protected void initializeSessionNegotiator(Channel ch, Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
                negotiatorFactory.getSessionNegotiator(this, ch, promise));
    }
}

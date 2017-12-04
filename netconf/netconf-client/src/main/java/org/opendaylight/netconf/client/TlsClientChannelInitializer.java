/*
 * Copyright (c) 2017 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;

import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.netconf.util.NetconfSslContextFactory;
import org.opendaylight.netconf.util.osgi.NetconfConfigUtil;
import org.opendaylight.netconf.util.osgi.NetconfConfiguration;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;


final class TlsClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    TlsClientChannelInitializer(final NetconfClientSessionNegotiatorFactory negotiatorFactory,
                                final NetconfClientSessionListener sessionListener) {
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
    }

    @Override
    public void initialize(Channel ch, Promise<NetconfClientSession> promise) {
        try {
            final NetconfConfiguration netconfConfiguration = NetconfConfigUtil.getNetconfConfigurationService(
                FrameworkUtil.getBundle(getClass()).getBundleContext());

            ch.pipeline().addFirst(NetconfSslContextFactory.getClientSslHandler(netconfConfiguration));
            super.initialize(ch, promise);
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void initializeSessionNegotiator(Channel ch, Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
                negotiatorFactory.getSessionNegotiator(() -> sessionListener, ch, promise));
    }
}

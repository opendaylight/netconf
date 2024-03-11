/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.restconf.nb.netty.NettyRestconf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyNorthboundInitializer implements TransportChannelListener {
    private static final Logger LOG = LoggerFactory.getLogger(NettyNorthboundInitializer.class);

    private final NettyRestconf nettyRestconf;

    public NettyNorthboundInitializer(final NettyRestconf nettyRestconf) {
        this.nettyRestconf = nettyRestconf;
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel channel) {
        LOG.debug("Transport channel {} established", channel);
        channel.channel().pipeline().addLast("restconf-server", nettyRestconf);
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        LOG.error("Transport channel failed", cause);
    }
}

/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.codec.EOMFrameDecoder;
import org.opendaylight.netconf.codec.EOMFrameEncoder;
import org.opendaylight.netconf.codec.FrameDecoder;
import org.opendaylight.netconf.codec.FrameEncoder;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.nettyutil.handler.HelloXMLMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfHelloMessageToXMLEncoder;

public abstract class AbstractChannelInitializer<S extends NetconfSession> {
    public static final String NETCONF_SESSION_NEGOTIATOR = "negotiator";

    public void initialize(final Channel ch, final Promise<S> promise) {
        ch.pipeline().addLast(FrameDecoder.HANDLER_NAME, new EOMFrameDecoder());
        initializeMessageDecoder(ch);
        ch.pipeline().addLast(FrameEncoder.HANDLER_NAME, new EOMFrameEncoder());
        initializeMessageEncoder(ch);

        initializeSessionNegotiator(ch, promise);
    }

    protected void initializeMessageEncoder(final Channel ch) {
        // Special encoding handler for hello message to include additional header if available,
        // it is thrown away after successful negotiation
        ch.pipeline().addLast(MessageEncoder.HANDLER_NAME, new NetconfHelloMessageToXMLEncoder());
    }

    protected void initializeMessageDecoder(final Channel ch) {
        // Special decoding handler for hello message to parse additional header if available,
        // it is thrown away after successful negotiation
        ch.pipeline().addLast(MessageDecoder.HANDLER_NAME, new HelloXMLMessageDecoder());
    }

    /**
     * Insert session negotiator into the pipeline. It must be inserted after message decoder
     * identified by {@link MessageDecoder#HANDLER_NAME}, (or any other custom decoder processor)
     */
    protected abstract void initializeSessionNegotiator(Channel ch, Promise<S> promise);
}

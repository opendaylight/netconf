/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.opendaylight.netconf.api.messages.FramingMechanism;

/**
 * A {@link FramingMechanismEncoder} handling {@link FramingMechanism#EOM}.
 */
public final class EOMFramingMechanismEncoder extends FramingMechanismEncoder {
    @Override
    protected void encode(final ChannelHandlerContext ctx, final ByteBuf msg, final ByteBuf out) {
        out.writeBytes(msg);
        out.writeBytes(MessageParts.END_OF_MESSAGE);
    }
}

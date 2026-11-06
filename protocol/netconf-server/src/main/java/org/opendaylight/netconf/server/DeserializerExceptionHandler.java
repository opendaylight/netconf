/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.Map;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.codec.FramingException;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DeserializerExceptionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(DeserializerExceptionHandler.class);

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.warn("An exception occurred during message handling", cause);

        final String message = cause.getMessage();
        final var future = SendErrorExceptionUtil.sendErrorMessage(ctx.channel(), new DocumentedException(message,
            ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.ERROR,
            message != null ? Map.of("cause", message) : Map.of()));

        if (cause instanceof FramingException) {
            // A framing error closes the session. See FramingException. Close it once the error reply is written, so
            // that the peer receives it.
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}

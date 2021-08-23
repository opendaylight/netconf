/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.util;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.util.messages.SendErrorExceptionUtil;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeserializerExceptionHandler implements ChannelHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DeserializerExceptionHandler.class);

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        // NOOP
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        // NOOP
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.warn("An exception occurred during message handling", cause);
        handleDeserializerException(ctx, cause);
    }

    private static void handleDeserializerException(final ChannelHandlerContext ctx, final Throwable cause) {
        final Map<String, String> info = new HashMap<>();
        info.put("cause", cause.getMessage());
        final DocumentedException ex = new DocumentedException(cause.getMessage(),
                ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.ERROR, info);

        SendErrorExceptionUtil.sendErrorMessage(ctx.channel(), ex);
    }
}

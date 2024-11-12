/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.channel.ChannelInboundHandler;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EventStreamResult;

/**
 *
 */
@NonNullByDefault
final class RestconfEventStreamResult implements EventStreamResult {

    @Override
    public ChannelInboundHandler toHandler(final HttpVersion version, final @Nullable Integer streamId) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
}

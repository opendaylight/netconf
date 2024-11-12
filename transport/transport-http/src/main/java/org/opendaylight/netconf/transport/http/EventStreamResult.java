/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelInboundHandler;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A request which has result in an HTTP event stream. It can be turned into a {@link ChannelInboundHandler} using
 * {@link #toHandler()}.
 */
@NonNullByDefault
public non-sealed interface EventStreamResult extends PreparedRequest {

    ChannelInboundHandler toHandler(HttpVersion version, @Nullable Integer streamId) throws IOException;
}

/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http2.Http2FrameListener;
import org.eclipse.jdt.annotation.Nullable;

// FIXME document purpose
public interface Http2FrameListenerProvider extends ChannelHandler {

    @Nullable Http2FrameListener getListenerFor(int streamId);
}

/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.util.AttributeKey;

public final class Http2Hooks {
    public static final AttributeKey<ChannelInitializer<Channel>> CHILD_INIT =
        AttributeKey.valueOf("http2.child.init");

    private Http2Hooks() {
        // no-op
    }
}

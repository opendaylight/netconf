/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2FrameListener;

/**
 * A concurrent HTTP/2+ server session.
 */
public abstract non-sealed class ConcurrentHTTPServerSession extends HTTPServerSession
        implements Http2Connection.Listener, Http2FrameListener {
    protected ConcurrentHTTPServerSession(final HTTPScheme scheme) {
        super(scheme);
    }
}

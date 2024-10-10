/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.WellKnownURI;

/**
 * The contents of the {@code host-meta} document, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc6415.html#section-3">RFC6415, section 3</a>.
 */
@NonNullByDefault
public final class HostMeta extends AbstractHostMeta {
    public HostMeta(final Map<URI, Link> links) {
        super(links);
    }

    @Override
    public WellKnownURI wellKnownUri() {
        return WellKnownURI.HOST_META;
    }

    @Override
    public FullHttpResponse toHttpResponse(final ByteBufAllocator alloc, final HttpVersion version) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
}
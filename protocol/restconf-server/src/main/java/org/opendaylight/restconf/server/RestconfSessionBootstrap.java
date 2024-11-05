/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2Settings;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.netconf.transport.http.HTTPServerSessionBootstrap;

@NonNullByDefault
final class RestconfSessionBootstrap extends HTTPServerSessionBootstrap {
    private final EndpointRoot root;

    RestconfSessionBootstrap(final HttpScheme scheme, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
    }

    @Override
    protected RestconfSession createHttp1Session() {
        return new RestconfSession(scheme, root);
    }

    @Override
    protected HTTPServerSession createHttp2Session(final Http2Settings settings) {
        throw new UnsupportedOperationException();
    }
}

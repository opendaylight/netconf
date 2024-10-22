/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpScheme;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
final class RestconfSession extends HTTPServerSession implements TransportSession {
    private final ArrayList<Registration> resources = new ArrayList<>();
    private final EndpointRoot root;

    RestconfSession(final HttpScheme scheme, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
    }

    @Override
    protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        return root.prepare(this, method, targetUri, headers);
    }

    @Override
    public void registerResource(final Registration registration) {
        resources.add(requireNonNull(registration));
    }

    @Override
    public List<Registration> getResources() {
        return ImmutableList.copyOf(resources);
    }

    void close() {
        resources.forEach(Registration::close);
        resources.clear();
    }
}

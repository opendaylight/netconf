/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl.operations;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.AbstractDataPendingGet;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.impl.NettyServerRequest;

/**
 * A GET or HEAD request to the /operations resource.
 */
@NonNullByDefault
final class PendingOperationsGet extends AbstractDataPendingGet {
    private final ApiPath apiPath;

    PendingOperationsGet(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding encoding, final ApiPath apiPath,
            final boolean withContent) {
        super(invariants, session, targetUri, principal, withContent, encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<FormattableBody> request) {
        if (apiPath.isEmpty()) {
            server().operationsGET(request);
        } else {
            server().operationsGET(request, apiPath);
        }
    }
}

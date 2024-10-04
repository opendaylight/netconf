/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.OptionsResult;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * An OPTIONS request to the /operations resource.
 */
@NonNullByDefault
final class PendingOperationsOptions extends AbstractPendingOptions {
    private final ApiPath apiPath;

    PendingOperationsOptions(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final ApiPath apiPath) {
        super(invariants, session, targetUri);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<OptionsResult> request, final InputStream body) {
        server().operationsOPTIONS(request, apiPath);
    }
}

/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.TransportSession;

/**
 * A GET or HEAD request to the /yang-library-version resource.
 */
@NonNullByDefault
final class PendingYangLibraryVersionGet extends AbstractDataPendingGet {
    PendingYangLibraryVersionGet(final EndpointInvariants invariants, final TransportSession session,
            final URI targetUri, final @Nullable Principal principal, final boolean withContent,
            final MessageEncoding encoding) {
        super(invariants, session, targetUri, principal, withContent, encoding);
    }

    @Override
    void execute(final NettyServerRequest<FormattableBody> request) {
        server().yangLibraryVersionGET(request);
    }
}

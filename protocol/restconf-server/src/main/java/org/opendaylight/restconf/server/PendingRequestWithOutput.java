/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * Abstract base class for {@link PendingRequestWithBody}s which also produce an output.
 *
 * @param <T> server response type
 * @param <B> request message body type
 */
@NonNullByDefault
abstract class PendingRequestWithOutput<T, B extends ConsumableBody> extends PendingRequestWithBody<T, B> {
    // Note naming: derived from 'Accept'
    final MessageEncoding acceptEncoding;
    final ApiPath apiPath;

    PendingRequestWithOutput(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding contentEncoding,
            final MessageEncoding acceptEncoding, final ApiPath apiPath) {
        super(invariants, session, targetUri, principal, contentEncoding);
        this.acceptEncoding = requireNonNull(acceptEncoding);
        this.apiPath = requireNonNull(apiPath);
    }
}

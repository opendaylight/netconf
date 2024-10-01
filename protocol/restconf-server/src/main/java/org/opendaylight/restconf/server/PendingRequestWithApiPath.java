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

/**
 * An {@link AbstractPendingRequest} with a corresponding (potentially empty) {@link ApiPath}.
 */
@NonNullByDefault
abstract class PendingRequestWithApiPath<T> extends AbstractPendingRequest<T> {
    final ApiPath apiPath;

    PendingRequestWithApiPath(final EndpointInvariants invariants, final URI targetUri,
            final @Nullable Principal principal, final ApiPath apiPath) {
        super(invariants, targetUri, principal);
        this.apiPath = requireNonNull(apiPath);
    }
}

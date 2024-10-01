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
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.XmlResourceBody;

/**
 * A {@link PendingRequestWithBody} with a {@link ResourceBody}.
 *
 * @param <T> server response type
 */
// TODO: From the semantic perspective we could use a better name, as we have exactly two subclasses:
//       - PatchPlain, i.e. 'merge into target resource'
//       - Put, i.e. 'create or replace target resource'
@NonNullByDefault
abstract sealed class PendingRequestWithResource<T> extends PendingRequestWithBody<T, ResourceBody>
        permits PendingDataPatchPlain, PendingDataPut {
    final ApiPath apiPath;

    PendingRequestWithResource(final EndpointInvariants invariants, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding contentEncoding, final ApiPath apiPath) {
        super(invariants, targetUri, principal, contentEncoding);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    final ResourceBody wrapBody(final InputStream body) {
        return switch (contentEncoding) {
            case JSON -> new JsonResourceBody(body);
            case XML -> new XmlResourceBody(body);
        };
    }
}

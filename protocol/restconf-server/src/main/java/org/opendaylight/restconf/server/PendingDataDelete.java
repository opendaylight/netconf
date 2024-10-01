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
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A DELETE request to the /data resource.
 */
@NonNullByDefault
final class PendingDataDelete extends PendingRequest<Empty> {
    private final ApiPath apiPath;

    PendingDataDelete(final EndpointInvariants invariants, final URI targetUri, final ApiPath apiPath) {
        super(invariants, targetUri);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<Empty> request, final InputStream body) {
        server().dataDELETE(request, apiPath);
    }

    @Override
    CompletedRequest transformResult(final NettyServerRequest<?> request, final Empty result) {
        return CompletedRequest.noContent();
    }
}

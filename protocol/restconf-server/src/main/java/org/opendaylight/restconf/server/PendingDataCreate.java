/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.ChildBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.JsonChildBody;
import org.opendaylight.restconf.server.api.XmlChildBody;

/**
 * A POST request to the /data resource.
 */
@NonNullByDefault
final class PendingDataCreate extends PendingRequestWithBody<CreateResourceResult, ChildBody> {
    PendingDataCreate(final EndpointInvariants invariants, final URI targetUri, final @Nullable Principal principal,
            final MessageEncoding contentEncoding) {
        super(invariants, targetUri, principal, contentEncoding);
    }

    @Override
    void execute(final NettyServerRequest<CreateResourceResult> request, final ChildBody body) {
        server().dataPOST(request, body);
    }

    @Override
    Response transformResult(final NettyServerRequest<?> request, final CreateResourceResult result) {
        return transformCreateResource(result);
    }

    @Override
    ChildBody wrapBody(final InputStream body) {
        return switch (contentEncoding) {
            case JSON -> new JsonChildBody(body);
            case XML -> new XmlChildBody(body);
        };
    }

}

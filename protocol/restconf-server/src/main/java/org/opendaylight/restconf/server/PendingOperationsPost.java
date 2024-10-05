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
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * A POST request to the /operations resource.
 */
@NonNullByDefault
final class PendingOperationsPost extends PendingRequestWithOutput<InvokeResult, OperationInputBody> {
    PendingOperationsPost(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding contentEncoding,
            final MessageEncoding acceptEncoding, final ApiPath apiPath) {
        super(invariants, session, targetUri, principal, contentEncoding, acceptEncoding, apiPath);
    }

    @Override
    void execute(final NettyServerRequest<InvokeResult> request, final OperationInputBody body) {
        server().operationsPOST(request, restconfURI(), apiPath, body);
    }

    @Override
    Response transformResult(final NettyServerRequest<?> request, final InvokeResult result) {
        return transformInvoke(request, result, acceptEncoding);
    }

    @Override
    OperationInputBody wrapBody(final InputStream body) {
        return switch (contentEncoding) {
            case JSON -> new JsonOperationInputBody(body);
            case XML -> new XmlOperationInputBody(body);
        };
    }
}

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
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;

/**
 * A POST request to the /operations resource.
 */
@NonNullByDefault
final class PendingOperationsPost extends PendingRequestWithBody<InvokeResult, OperationInputBody> {
    private final ApiPath apiPath;

    PendingOperationsPost(final EndpointInvariants invariants, final URI targetUri, final MessageEncoding encoding,
            final ApiPath apiPath) {
        super(invariants, targetUri, encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<InvokeResult> request, final OperationInputBody body) {
        server().operationsPOST(request, restconfURI(), apiPath, body);
    }

    @Override
    Response transformResult(final NettyServerRequest<?> request, final InvokeResult result) {
        final var output = result.output();
        return output == null ? CompletedRequest.noContent()
            : new FormattableDataResponse(output, encoding, request.prettyPrint());
    }

    @Override
    OperationInputBody wrapBody(final InputStream body) {
        return switch (encoding) {
            case JSON -> new JsonOperationInputBody(body);
            case XML -> new XmlOperationInputBody(body);
        };
    }
}

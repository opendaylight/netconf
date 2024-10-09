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
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataPostBody;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.XmlDataPostBody;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * A POST request to a child of /data resource.
 */
@NonNullByDefault
final class PendingDataPost extends PendingRequestWithOutput<DataPostResult, DataPostBody> {
    PendingDataPost(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding contentEncoding,
            final MessageEncoding acceptEncoding, final ApiPath apiPath) {
        super(invariants, session, targetUri, principal, contentEncoding, acceptEncoding, apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataPostResult> request, final DataPostBody body) {
        server().dataPOST(request, apiPath, body);
    }

    @Override
    Response transformResult(final NettyServerRequest<?> request, final DataPostResult result) {
        return switch (result) {
            case CreateResourceResult create -> transformCreateResource(create);
            case InvokeResult invoke -> transformInvoke(request, invoke, acceptEncoding);
        };
    }

    @Override
    DataPostBody wrapBody(final InputStream body) {
        return switch (contentEncoding) {
            case JSON -> new JsonDataPostBody(body);
            case XML -> new XmlDataPostBody(body);
        };
    }
}

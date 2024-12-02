/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * A GET or HEAD request to the /data resource.
 */
@NonNullByDefault
final class PendingDataGet extends AbstractPendingGet<DataGetResult> {
    private final MessageEncoding encoding;
    private final ApiPath apiPath;

    PendingDataGet(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final boolean withContent, final MessageEncoding encoding,
            final ApiPath apiPath) {
        super(invariants, session, targetUri, principal, withContent);
        this.encoding = requireNonNull(encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    MessageEncoding errorEncoding() {
        return encoding;
    }

    @Override
    void execute(final NettyServerRequest<DataGetResult> request) {
        if (apiPath.isEmpty()) {
            server().dataGET(request);
        } else {
            server().dataGET(request, apiPath);
        }
    }

    @Override
    Response transformResultImpl(final NettyServerRequest<?> request, final DataGetResult result) {
        final var headers = metadataHeaders(result);
        headers.add(HttpHeaderNames.CACHE_CONTROL);
        headers.add(HttpHeaderValues.NO_CACHE);

        return new FormattableDataResponse(HttpResponseStatus.OK, result.body(), encoding, request.prettyPrint(),
            headers);
    }

    @Override
    List<CharSequence> extractHeaders(final DataGetResult result) {
        final var headers = metadataHeaders(result);
        headers.add(HttpHeaderNames.CACHE_CONTROL);
        headers.add(HttpHeaderValues.NO_CACHE);
        headers.add(HttpHeaderNames.CONTENT_TYPE);
        headers.add(encoding.dataMediaType());
        return headers;
    }
}

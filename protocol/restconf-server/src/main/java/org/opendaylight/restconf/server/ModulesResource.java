/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.net.URI;
import java.security.Principal;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * Access to YANG modules. The fact this sits underneath RESTCONF only due to historical reasons.
 */
@NonNullByDefault
final class ModulesResource extends AbstractLeafResource {
    ModulesResource(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final @Nullable Principal principal, final String path) {
        return switch (method) {
            case GET -> prepareGet(session, targetUri, headers, principal, path, true);
            case HEAD -> prepareGet(session, targetUri, headers, principal, path, false);
            case OPTIONS -> AbstractPendingOptions.READ_ONLY;
            default -> METHOD_NOT_ALLOWED_READ_ONLY;
        };
    }

    private PreparedRequest prepareGet(final TransportSession session, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean withContent) {
        if (path.isEmpty()) {
            return EmptyResponse.NOT_FOUND;
        }

        // optional mountPath followed by file name separated by slash
        final var str = path.substring(1);
        final var lastSlash = str.lastIndexOf('/');
        final ApiPath mountPath;
        final String fileName;
        if (lastSlash != -1) {
            final var mountString = str.substring(0, lastSlash);
            try {
                mountPath = ApiPath.parse(mountString);
            } catch (ParseException e) {
                return badApiPath(mountString, e);
            }
            fileName = str.substring(lastSlash + 1);
        } else {
            mountPath = ApiPath.empty();
            fileName = str;
        }

        if (fileName.isEmpty()) {
            return EmptyResponse.NOT_FOUND;
        }

        // YIN if explicitly requested
        // YANG by default, incl accept any
        // FIXME: we should use client's preferences
        final var doYin = headers.contains(HttpHeaderNames.ACCEPT, NettyMediaTypes.APPLICATION_YIN_XML, true)
            && !headers.contains(HttpHeaderNames.ACCEPT, NettyMediaTypes.APPLICATION_YANG, true);
        final var decoded = QueryStringDecoder.decodeComponent(fileName);

        return doYin
            ? new PendingModulesGetYin(invariants, session, targetUri, principal, withContent, mountPath, decoded)
            : new PendingModulesGetYang(invariants, session, targetUri, principal, withContent, mountPath, decoded);
    }
}

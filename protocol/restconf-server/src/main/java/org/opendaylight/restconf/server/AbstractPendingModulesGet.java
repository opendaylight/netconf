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
import io.netty.util.AsciiString;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.ByteSourceResponse;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * An abstract class for implementations of a GET or HEAD request to the /modules resource.
 */
@NonNullByDefault
abstract sealed class AbstractPendingModulesGet extends AbstractPendingGet<ModulesGetResult>
        permits PendingModulesGetYang, PendingModulesGetYin {
    private final ApiPath mountPath;
    private final String fileName;

    AbstractPendingModulesGet(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final boolean withContent, final ApiPath mountPath,
            final String fileName) {
        super(invariants, session, targetUri, principal, withContent);
        this.mountPath = requireNonNull(mountPath);
        this.fileName = requireNonNull(fileName);
    }

    @Override
    final void execute(final NettyServerRequest<ModulesGetResult> request) {
        final var revision = request.queryParameters().lookup("revision");
        if (mountPath.isEmpty()) {
            execute(request, fileName, revision);
        } else {
            execute(request, mountPath, fileName, revision);
        }
    }

    abstract void execute(NettyServerRequest<ModulesGetResult> request, String fileName, @Nullable String revision);

    abstract void execute(NettyServerRequest<ModulesGetResult> request, ApiPath mountPath, String fileName,
        @Nullable String revision);

    @Override
    final Response transformResultImpl(final NettyServerRequest<?> request, final ModulesGetResult result) {
        return new ByteSourceResponse(result.source().asByteSource(StandardCharsets.UTF_8), mediaType());
    }

    @Override
    final List<CharSequence> extractHeaders(final ModulesGetResult result) {
        return List.of(HttpHeaderNames.CONTENT_TYPE, mediaType());
    }

    abstract AsciiString mediaType();
}

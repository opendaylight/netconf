/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ModulesGetResult;

/**
 * A GET or HEAD request to the /modules resource.
 */
@NonNullByDefault
final class PendingModulesGet extends PendingRequest<ModulesGetResult> {
    @VisibleForTesting
    static final String SOURCE_READ_FAILURE_ERROR = "Failure reading module source: ";
    @VisibleForTesting
    static final String REVISION = "revision";

    private final ApiPath mountPath;
    private final String fileName;
    private final boolean yinInstead;

    PendingModulesGet(final EndpointInvariants invariants, final URI targetUri, final ApiPath mountPath,
            final String fileName, final boolean yinInstead) {
        super(invariants, targetUri);
        this.mountPath = requireNonNull(mountPath);
        this.fileName = requireNonNull(fileName);
        this.yinInstead = yinInstead;
    }

    @Override
    void execute(final NettyServerRequest<ModulesGetResult> request, final InputStream body) {
        final var revision = request.queryParameters().lookup(REVISION);
        final var server = server();
        if (yinInstead) {
            if (mountPath.isEmpty()) {
                server.modulesYinGET(request, fileName, revision);
            } else {
                server.modulesYinGET(request, mountPath, fileName, revision);
            }
        } else if (mountPath.isEmpty()) {
            server.modulesYangGET(request, fileName, revision);
        } else {
            server.modulesYangGET(request, mountPath, fileName, revision);
        }
    }

    @Override
    ByteSourceResponse transformResult(final NettyServerRequest<?> request, final ModulesGetResult result) {
        return new ByteSourceResponse(result.source().asByteSource(StandardCharsets.UTF_8),
            DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
                .set(HttpHeaderNames.CONTENT_TYPE,
                    yinInstead ? NettyMediaTypes.APPLICATION_YIN_XML : NettyMediaTypes.APPLICATION_YANG));
    }
}

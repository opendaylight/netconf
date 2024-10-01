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
import io.netty.util.AsciiString;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ModulesGetResult;

/**
 * An abstract class for implementations of a GET or HEAD request to the /modules resource.
 */
@NonNullByDefault
abstract sealed class AbstractPendingModulesGet extends PendingRequest<ModulesGetResult>
        permits PendingModulesGetYang, PendingModulesGetYin {
    @VisibleForTesting
    static final String SOURCE_READ_FAILURE_ERROR = "Failure reading module source: ";
    @VisibleForTesting
    static final String REVISION = "revision";

    private final ApiPath mountPath;
    private final String fileName;

    AbstractPendingModulesGet(final EndpointInvariants invariants, final URI targetUri, final ApiPath mountPath,
            final String fileName) {
        super(invariants, targetUri);
        this.mountPath = requireNonNull(mountPath);
        this.fileName = requireNonNull(fileName);
    }

    @Override
    final void execute(final NettyServerRequest<ModulesGetResult> request, final InputStream body) {
        final var revision = request.queryParameters().lookup(REVISION);
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
    final ByteSourceResponse transformResult(final NettyServerRequest<?> request, final ModulesGetResult result) {
        return new ByteSourceResponse(result.source().asByteSource(StandardCharsets.UTF_8),
            DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
                .set(HttpHeaderNames.CONTENT_TYPE, mediaType()));
    }

    abstract AsciiString mediaType();
}

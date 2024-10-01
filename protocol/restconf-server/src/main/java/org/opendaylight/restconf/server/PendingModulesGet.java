/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.ResponseUtils.simpleResponse;

import com.google.common.annotations.VisibleForTesting;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 * A GET or HEAD request to the /modules resource.
 */
@NonNullByDefault
final class PendingModulesGet extends PendingRequest {
    @VisibleForTesting
    static final String SOURCE_READ_FAILURE_ERROR = "Failure reading module source: ";

    private final ApiPath mountPath;
    private final String fileName;
    private final boolean yinInstead;

    PendingModulesGet(final ApiPath mountPath, final String fileName, final boolean yinInstead) {
        this.mountPath = requireNonNull(mountPath);
        this.fileName = requireNonNull(fileName);
        this.yinInstead = yinInstead;
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {

//        final var revision = params.queryParameters().lookup(REVISION);

//        if (yinInstead) {
//            final var request = getModuleRequest(params, callback, NettyMediaTypes.APPLICATION_YIN_XML);
//            if (mountPath.isEmpty()) {
//                server.modulesYinGET(request, fileName, revision);
//            } else {
//                server.modulesYinGET(request, mountPath, fileName, revision);
//            }
//        } else {
//            final var request = getModuleRequest(params, callback, NettyMediaTypes.APPLICATION_YANG);
//            if (mountPath.isEmpty()) {
//                server.modulesYangGET(request, fileName, revision);
//            } else {
//                server.modulesYangGET(request, mountPath, fileName, revision);
//            }
//        }
    }

    private static ServerRequest<ModulesGetResult> getModuleRequest(final RequestParameters params,
            final RestconfRequest callback, final AsciiString mediaType) {
        return new NettyServerRequest<>(params, callback) {
            @Override
            FullHttpResponse transform(final ModulesGetResult result) {
                final byte[] bytes;
                try {
                    bytes = result.source().asByteSource(StandardCharsets.UTF_8).read();
                } catch (IOException e) {
                    throw new ServerErrorException(ErrorTag.OPERATION_FAILED,
                        SOURCE_READ_FAILURE_ERROR + e.getMessage(), e);
                }
                return simpleResponse(requestParams, HttpResponseStatus.OK, mediaType, bytes);
            }
        };
    }
}

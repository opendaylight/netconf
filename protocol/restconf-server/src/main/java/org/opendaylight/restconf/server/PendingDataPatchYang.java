/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.ResponseUtils.responseStatus;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;

/**
 * A PATCH request to the /data resource with YANG Data payload.
 */
@NonNullByDefault
final class PendingDataPatchYang extends PendingRequest {
    private final MessageEncoding encoding;
    private final ApiPath apiPath;

    PendingDataPatchYang(final MessageEncoding encoding, final ApiPath apiPath) {
        this.encoding = requireNonNull(encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    // FIXME: stuff
    void execute(final RestconfServer server) {
//        final var request = new NettyServerRequest<DataYangPatchResult>(params, callback) {
//            @Override
//            FullHttpResponse transform(final DataYangPatchResult result) {
//                final var patchStatus = result.status();
//                return responseBuilder(requestParams, patchResponseStatus(patchStatus, requestParams.errorTagMapping()))
//                    .setBody(new YangPatchStatusBody(patchStatus))
//                    .setMetadataHeaders(result)
//                    .build();
//            }
//        };
//        final var yangPatchBody = requestBody(params, JsonPatchBody::new, XmlPatchBody::new);
//        if (apiPath.isEmpty()) {
//            server.dataPATCH(request, yangPatchBody);
//        } else {
//            server.dataPATCH(request, apiPath, yangPatchBody);
//        }
    }

    private static HttpResponseStatus patchResponseStatus(final PatchStatusContext statusContext,
            final ErrorTagMapping errorTagMapping) {
        if (statusContext.ok()) {
            return HttpResponseStatus.OK;
        }
        final var globalErrors = statusContext.globalErrors();
        if (globalErrors != null && !globalErrors.isEmpty()) {
            return responseStatus(globalErrors.getFirst().tag(), errorTagMapping);
        }
        for (var edit : statusContext.editCollection()) {
            if (!edit.isOk()) {
                final var editErrors = edit.getEditErrors();
                if (editErrors != null && !editErrors.isEmpty()) {
                    return responseStatus(editErrors.getFirst().tag(), errorTagMapping);
                }
            }
        }
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }
}

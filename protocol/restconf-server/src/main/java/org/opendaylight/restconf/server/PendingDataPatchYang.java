/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.JsonPatchBody;
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.XmlPatchBody;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 * A PATCH request to the /data resource with YANG Data payload.
 */
@NonNullByDefault
final class PendingDataPatchYang extends PendingRequestWithBody<DataYangPatchResult, PatchBody> {
    private final ApiPath apiPath;

    PendingDataPatchYang(final EndpointInvariants invariants, final URI targetUri, final MessageEncoding encoding,
            final ApiPath apiPath) {
        super(invariants, targetUri, encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataYangPatchResult> request, final PatchBody body) {
        if (apiPath.isEmpty()) {
            server().dataPATCH(request, body);
        } else {
            server().dataPATCH(request, apiPath, body);
        }
    }

    @Override
    Response transformResult(final NettyServerRequest<?> request, final DataYangPatchResult result) {
//      final var patchStatus = result.status();
//      return responseBuilder(requestParams, patchResponseStatus(patchStatus, requestParams.errorTagMapping()))
//          .setBody(new YangPatchStatusBody(patchStatus))
//          .setMetadataHeaders(result)
//          .build();
        throw new UnsupportedOperationException();
    }

    @Override
    PatchBody wrapBody(final InputStream body) {
        return switch (encoding) {
            case JSON -> new JsonPatchBody(body);
            case XML -> new XmlPatchBody(body);
        };
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

    private static HttpResponseStatus responseStatus(final ErrorTag errorTag, final ErrorTagMapping errorTagMapping) {
        final var statusCode = errorTagMapping.statusOf(errorTag).code();
        return HttpResponseStatus.valueOf(statusCode);
    }
}

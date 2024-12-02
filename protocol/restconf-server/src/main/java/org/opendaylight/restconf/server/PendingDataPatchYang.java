/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.JsonPatchBody;
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.XmlPatchBody;
import org.opendaylight.restconf.server.impl.EndpointInvariants;
import org.opendaylight.restconf.server.spi.YangPatchStatusBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 * A PATCH request to the /data resource with YANG Data payload.
 */
@NonNullByDefault
final class PendingDataPatchYang extends PendingRequestWithOutput<DataYangPatchResult, PatchBody> {
    PendingDataPatchYang(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal, final MessageEncoding contentEncoding,
            final MessageEncoding acceptEncoding, final ApiPath apiPath) {
        super(invariants, session, targetUri, principal, contentEncoding, acceptEncoding, apiPath);
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
        final var patchStatus = result.status();
        return new FormattableDataResponse(patchResponseStatus(patchStatus), new YangPatchStatusBody(patchStatus),
            acceptEncoding, request.prettyPrint(), metadataHeaders(result));
    }

    @Override
    PatchBody wrapBody(final InputStream body) {
        return switch (contentEncoding) {
            case JSON -> new JsonPatchBody(body);
            case XML -> new XmlPatchBody(body);
        };
    }

    private HttpResponseStatus patchResponseStatus(final PatchStatusContext statusContext) {
        if (statusContext.ok()) {
            return HttpResponseStatus.OK;
        }
        final var globalErrors = statusContext.globalErrors();
        if (globalErrors != null && !globalErrors.isEmpty()) {
            return responseStatus(globalErrors.getFirst().tag());
        }
        for (var edit : statusContext.editCollection()) {
            if (!edit.isOk()) {
                final var editErrors = edit.getEditErrors();
                if (editErrors != null && !editErrors.isEmpty()) {
                    return responseStatus(editErrors.getFirst().tag());
                }
            }
        }
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }

    private HttpResponseStatus responseStatus(final ErrorTag errorTag) {
        return HttpResponseStatus.valueOf(invariants.errorTagMapping().statusOf(errorTag).code());
    }
}

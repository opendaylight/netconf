/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import java.util.List;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.ErrorTagMapping;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.spi.YangPatchStatusBody;

@NonNullByDefault
final class DataYangPatchRequest extends JaxRsServerRequest<DataYangPatchResult> {
    DataYangPatchRequest(final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping,
            final UriInfo uriInfo, final AsyncResponse ar) {
        super(defaultPrettyPrint, errorTagMapping, uriInfo, ar);
    }

    @Override
    Response createResponse(final PrettyPrintParam prettyPrint, final DataYangPatchResult result)
            throws ServerException {
        final var patchStatus = result.status();
        final var statusCode = statusOf(patchStatus);
        final var builder = Response.status(statusCode.code(), statusCode.phrase())
            .entity(new YangPatchStatusBody(patchStatus));
        fillConfigurationMetadata(builder, result);
        return builder.build();
    }

    private HttpStatusCode statusOf(final PatchStatusContext result) {
        if (result.ok()) {
            return HttpStatusCode.OK;
        }
        final var globalErrors = result.globalErrors();
        if (globalErrors != null && !globalErrors.isEmpty()) {
            return statusOfFirst(globalErrors);
        }
        for (var edit : result.editCollection()) {
            if (!edit.isOk()) {
                final var editErrors = edit.getEditErrors();
                if (editErrors != null && !editErrors.isEmpty()) {
                    return statusOfFirst(editErrors);
                }
            }
        }
        return HttpStatusCode.INTERNAL_SERVER_ERROR;
    }

    private HttpStatusCode statusOfFirst(final List<ServerError> error) {
        return statusOf(error.get(0).tag());
    }
}

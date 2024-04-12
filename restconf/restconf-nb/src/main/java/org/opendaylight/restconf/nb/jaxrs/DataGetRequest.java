/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.ErrorTagMapping;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.ServerException;

@NonNullByDefault
final class DataGetRequest extends JaxRsServerRequest<DataGetResult> {
    private static final CacheControl NO_CACHE = CacheControl.valueOf("no-cache");

    DataGetRequest(final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping,
            final UriInfo uriInfo, final AsyncResponse ar) {
        super(defaultPrettyPrint, errorTagMapping, uriInfo, ar);
    }

    @Override
    Response createResponse(final PrettyPrintParam prettyPrint, final DataGetResult result) throws ServerException {
        final var builder = Response.ok()
            .entity(new JaxRsFormattableBody(result.body(), prettyPrint))
            .cacheControl(NO_CACHE);
        fillConfigurationMetadata(builder, result);
        return builder.build();
    }
}

/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import java.io.InputStream;
import java.text.ParseException;
import java.util.function.Function;
import javax.ws.rs.BadRequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.server.api.ChildBody;
import org.opendaylight.restconf.server.api.DataPostBody;
import org.opendaylight.restconf.server.api.JsonChildBody;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.api.XmlChildBody;
import org.opendaylight.restconf.server.api.XmlDataPostBody;

final class RequestUtils {
    // FIXME: document this header
    static final String RESTCONF_AUTHN_HEADER = "x-restconf-authn";

    private RequestUtils() {
        // hidden on purpose
    }

    static ApiPath extractApiPath(final RequestParameters params) {
        try {
            return ApiPath.parse(params.pathParameters().childIdentifier());
        } catch (ParseException e) {
            // FIXME use proper exception to build expected RESTCONF response
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    // FIXME: this needs to be integrated with the callers
    static <T> NettyServerRequest<T> buildServerRequest(final RequestParameters params) {
        try {
            final var queryParams = QueryParameters.ofMultiValue(params.queryParameters());
            return ServerRequest.of(queryParams, params.defaultPrettyPrint());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    static ChildBody childBody(final RequestParameters params) {
        return requestBody(params, ChildBody.class, JsonChildBody::new, XmlChildBody::new);
    }

    static DataPostBody dataPostBody(final RequestParameters params) {
        return requestBody(params, DataPostBody.class, JsonDataPostBody::new, XmlDataPostBody::new);
    }

    private static <T extends ConsumableBody> T requestBody(final RequestParameters params, final Class<T> type,
            final Function<InputStream, T> jsonBodyBuilder, final Function<InputStream, T> xmlBodyBuilder) {
        return ContentTypes.JSON_TYPES.contains(params.contentType())
            ? jsonBodyBuilder.apply(params.requestBody()) : xmlBodyBuilder.apply(params.requestBody());
    }
}

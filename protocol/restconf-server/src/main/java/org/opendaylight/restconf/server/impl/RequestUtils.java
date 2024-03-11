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
import java.util.regex.Pattern;
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

    private RequestUtils() {
        // utility class
    }

    static ApiPath apiPath(final Pattern pattern, final RequestContext context) {
        final var matcher = pattern.matcher(context.contextPath());
        if (matcher.matches() && matcher.groupCount() > 1) {
            try {
                return ApiPath.parse(matcher.group(1));
            } catch (ParseException e) {
                // FIXME this should cause 404 due to path is invalid
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        return ApiPath.empty();
    }

    static ServerRequest serverRequest(final RequestContext context) {
        try {
            final var params = QueryParameters.ofMultiValue(context.queryParameters());
            return ServerRequest.of(params, context.defaultPrettyPrint());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    static ChildBody childBody(final RequestContext context) {
        return requestBody(context, ChildBody.class, JsonChildBody::new, XmlChildBody::new);
    }

    static DataPostBody dataPostBody(final RequestContext context) {
        return requestBody(context, DataPostBody.class, JsonDataPostBody::new, XmlDataPostBody::new);
    }

    private static <T extends ConsumableBody> T requestBody(final RequestContext context, Class<T> type,
            final Function<InputStream, T> jsonBodyBuilder, final Function<InputStream, T> xmlBodyBuilder) {
        return ContentTypes.isJson(context.contentType())
            ? jsonBodyBuilder.apply(context.requestBody()) : xmlBodyBuilder.apply(context.requestBody());
    }
}

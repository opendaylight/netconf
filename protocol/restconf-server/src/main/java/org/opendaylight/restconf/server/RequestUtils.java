/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.io.InputStream;
import java.text.ParseException;
import java.util.function.Function;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;

final class RequestUtils {

    private RequestUtils() {
        // hidden on purpose
    }

    static ApiPath extractApiPath(final RequestParameters params) {
        return extractApiPath(params.pathParameters().childIdentifier());
    }

    static ApiPath extractApiPath(final String path) {
        try {
            return ApiPath.parse(path);
        } catch (ParseException e) {
            throw new ServerErrorException(ErrorTag.BAD_ATTRIBUTE,
                "API Path value '%s' is invalid. %s".formatted(path, e.getMessage()), e);
        }
    }

    static <T extends ConsumableBody> T requestBody(final RequestParameters params,
            final Function<InputStream, T> jsonBodyBuilder, final Function<InputStream, T> xmlBodyBuilder) {
        return NettyMediaTypes.JSON_TYPES.contains(params.contentType())
            ? jsonBodyBuilder.apply(params.requestBody()) : xmlBodyBuilder.apply(params.requestBody());
    }
}

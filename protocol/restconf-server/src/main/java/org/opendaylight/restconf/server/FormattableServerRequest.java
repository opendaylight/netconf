/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.opendaylight.restconf.server.ResponseUtils.responseBuilder;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;

/**
 * A {@link NettyServerRequest} resulting in a {@link FormattableBody}.
 */
@NonNullByDefault
final class FormattableServerRequest extends NettyServerRequest<FormattableBody> {
    FormattableServerRequest(final RequestParameters requestParameters, final RestconfRequest callback) {
        super(requestParameters, callback);
    }

    @Override
    FullHttpResponse transform(final FormattableBody result) {
        return responseBuilder(requestParameters, HttpResponseStatus.OK).setBody(result).build();
    }
}

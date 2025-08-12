/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.AbstractFiniteResponse;
import org.opendaylight.netconf.transport.http.ResponseOutput;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;

@NonNullByDefault
final class EntityRequestResponse extends AbstractFiniteResponse {
    private static final JsonFactory FACTORY = JsonFactory.builder().build();
    private static final ReadOnlyHttpHeaders HEADERS =
        new ReadOnlyHttpHeaders(false, HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);

    private final OpenApiEntity entity;

    EntityRequestResponse(final OpenApiEntity entity) {
        super(HttpResponseStatus.OK);
        this.entity = requireNonNull(entity);
    }

    @Override
    public void writeTo(final ResponseOutput output) throws IOException {
        try (var out = output.start(status(), HEADERS)) {
            final var generator = FACTORY.createGenerator(out);
            try {
                entity.generate(generator);
            } catch (IOException e) {
                out.handleError(e);
                throw e;
            }
            generator.flush();
        }
    }
}

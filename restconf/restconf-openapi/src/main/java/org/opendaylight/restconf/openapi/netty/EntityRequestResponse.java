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
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ByteBufResponse;
import org.opendaylight.netconf.transport.http.ByteStreamRequestResponse;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;

@NonNullByDefault
final class EntityRequestResponse extends ByteStreamRequestResponse {
    private static final JsonFactory FACTORY = JsonFactory.builder().build();

    private final OpenApiEntity entity;

    EntityRequestResponse(final OpenApiEntity entity) {
        super(HttpResponseStatus.OK);
        this.entity = requireNonNull(entity);
    }

    @Override
    protected ByteBufResponse toReadyResponse(final ByteBuf content) {
        return new ByteBufResponse(status(), content, HttpHeaderValues.APPLICATION_JSON);
    }

    @Override
    protected void writeBody(final OutputStream out) throws IOException {
        final var generator = FACTORY.createGenerator(out);
        entity.generate(generator);
        generator.flush();
    }
}

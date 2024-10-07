/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;

/**
 * Temporary class to aid migration to direct writeout.
 */
final class OpenApiBodyBuffer {
    private final JsonGenerator generator;
    private final ByteArrayOutputStream stream;

    OpenApiBodyBuffer(final JsonGenerator generator, final ByteArrayOutputStream stream) {
        this.generator = requireNonNull(generator);
        this.stream = stream;
    }

    ByteArrayInputStream entityInputStream(final OpenApiEntity entity) throws IOException {
        entity.generate(generator);
        generator.flush();
        final var bais = new ByteArrayInputStream(stream.toByteArray());
        stream.reset();
        return bais;
    }
}

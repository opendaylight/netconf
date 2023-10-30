/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public final class HttpEntity extends SecuritySchemeEntity {
    private static final Type TYPE = Type.http;

    private final String scheme;
    private final String description;
    private final String bearerFormat;

    public HttpEntity(@NonNull final String scheme, @Nullable final String description,
            final @Nullable String bearerFormat) {
        this.scheme = requireNonNull(scheme);
        this.description = description;
        this.bearerFormat = bearerFormat;
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public void generate(@NonNull final JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("type", type().toString());
        generator.writeStringField("scheme", scheme);
        if (description != null) {
            generator.writeStringField("description", description);
        }
        if (bearerFormat != null) {
            generator.writeStringField("bearerFormat", bearerFormat);
        }
        generator.writeEndObject();
    }

    @Override
    public String toString() {
        return "Http[type=" + TYPE
            + ", scheme=" + scheme
            + ", description=" + description
            + ", bearerFormat=" + bearerFormat + "]";
    }

    public String scheme() {
        return scheme;
    }

    public String description() {
        return description;
    }

    public String bearerFormat() {
        return bearerFormat;
    }
}

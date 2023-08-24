/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Archetype for a Schema.
 */
public final class SchemaEntity extends OpenApiEntity {
    private final SchemaNode value;
    private final String type;

    public SchemaEntity(final @NonNull SchemaNode value, final String type) {
        this.value = value;
        this.type = type;
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        final var title = title();
        if (title != null) {
            generator.writeStringField("title", title);
        }
        final var type = type();
        if (type != null) {
            generator.writeStringField("type", type);
        }
        final var description = description();
        if (description != null) {
            generator.writeStringField("description", description);
        }
        final var reference = reference();
        if (reference != null) {
            generator.writeStringField("$ref", reference);
        }
        generateEnum(generator);
        generateRequired(generator);
        generateDiscriminator(generator);
        generateExamples(generator);
        generateExternalDocs(generator);
        generateProperties(generator);
        generator.writeEndObject();
    }

    @Nullable String title() {
        return value.getQName().getLocalName();
    }

    @Nullable String type() {
        return type;
    }

    @Nullable String description() {
        return value.getDescription().orElse("");
    }

    @Nullable String reference() {
        return null;
    }

    void generateEnum(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    void generateRequired(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    void generateDiscriminator(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    void generateExamples(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    void generateExternalDocs(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    void generateProperties(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    void generateXml(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }
}

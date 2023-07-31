/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Archetype for a Schema.
 */
public final class SchemaEntity extends OpenApiEntity {
    private final @NonNull String title;
    private final @NonNull String type;

    public SchemaEntity(final @NonNull String title, final @NonNull String type) {
        this.title = requireNonNull(title);
        this.type = requireNonNull(type);
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(title());
        generator.writeStringField("title", title());
        generator.writeStringField("type", type());
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
        generateXml(generator);
        generator.writeEndObject();
    }

    private @NonNull String title() {
        return title;
    }

    private @NonNull String type() {
        return type;
    }

    private @Nullable String description() {
        return null;
    }

    private @Nullable String reference() {
        return null;
    }

    private void generateEnum(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    private void generateRequired(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    private void generateDiscriminator(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    private void generateExamples(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    private void generateExternalDocs(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    private void generateProperties(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    private void generateXml(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }
}

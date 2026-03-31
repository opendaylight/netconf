/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Archetype for a Schema.
 */
public abstract sealed class SchemaEntity extends OpenApiEntity permits NodeSchemaEntity, RpcSchemaEntity {
    private final @NonNull SchemaNode value;
    private final @NonNull String title;
    private final @NonNull String discriminator;
    private final @NonNull String type;
    private final @NonNull SchemaInferenceStack stack;
    private final boolean isParentConfig;
    private final @NonNull String parentName;
    private final @NonNull DefinitionNames definitionNames;
    protected final int width;
    protected final int depth;
    protected final int nodeDepth;

    public SchemaEntity(final @NonNull SchemaNode value, final @NonNull String title,
            final @Nullable String discriminator, final @NonNull String type,
            final @NonNull SchemaInferenceStack context, final @NonNull String parentName, final boolean isParentConfig,
            final @NonNull DefinitionNames definitionNames, final int width,
            final int depth, final int nodeDepth) {
        this.value = requireNonNull(value);
        this.title = requireNonNull(title);
        this.type = requireNonNull(type);
        this.stack = requireNonNull(context.copy());
        this.parentName = requireNonNull(parentName);
        this.isParentConfig = isParentConfig;
        this.definitionNames = requireNonNull(definitionNames);
        this.discriminator = requireNonNullElse(discriminator, "");
        this.width = width;
        this.depth = depth;
        this.nodeDepth = nodeDepth;
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(title() + discriminator);
        generator.writeStringField("title", title());
        generator.writeStringField("type", type());
        final var description = description();
        if (description != null) {
            generator.writeStringField("description", description);
        }
        generateProperties(generator);
        generateXml(generator);
        generator.writeEndObject();
    }

    protected @NonNull SchemaNode value() {
        return value;
    }

    protected @NonNull SchemaInferenceStack stack() {
        return stack;
    }

    protected @NonNull DefinitionNames definitionNames() {
        return definitionNames;
    }

    protected boolean isParentConfig() {
        return isParentConfig;
    }

    protected @NonNull String parentName() {
        return parentName;
    }

    private @NonNull String title() {
        return title;
    }

    private @NonNull String type() {
        return type;
    }

    private @Nullable String description() {
        return value.getDescription().orElse(null);
    }

    protected void generateRequired(final @NonNull JsonGenerator generator, final List<String> required)
            throws IOException {
        if (!required.isEmpty()) {
            generator.writeArrayFieldStart("required");
            for (final var req : required) {
                generator.writeString(req);
            }
            generator.writeEndArray();
        }
    }

    private void generateProperties(@NonNull JsonGenerator generator) throws IOException {
        final var required = new ArrayList<String>();
        generator.writeObjectFieldStart("properties");
        stack().enterSchemaTree(value().getQName());
        generateProperties(generator, required);
        stack().exit();
        generator.writeEndObject();
        generateRequired(generator, required);
    }

    abstract void generateProperties(@NonNull JsonGenerator generator, @NonNull List<String> required)
        throws IOException;

    private void generateXml(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("xml");
        generator.writeStringField("name", value.getQName().getLocalName());
        generator.writeStringField("namespace", value.getQName().getNamespace().toString());
        generator.writeEndObject();
    }
}

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
import static org.opendaylight.restconf.openapi.model.PropertyEntity.isSchemaNodeMandatory;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.restconf.openapi.impl.SchemasStream;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Archetype for a Schema.
 */
public final class SchemaEntity extends OpenApiEntity {
    private final @NonNull SchemaNode value;
    private final @NonNull String title;
    private final @NonNull String discriminator;
    private final @NonNull String type;
    private final @NonNull SchemaInferenceStack stack;
    private final boolean isParentConfig;
    private final SchemasStream.EntityType entityType;
    private final @NonNull String parentName;
    private final @NonNull DefinitionNames definitionNames;

    public SchemaEntity(final @NonNull SchemaNode value, final @NonNull String title, final String discriminator,
            @NonNull final String type, @NonNull final SchemaInferenceStack context, final String parentName,
            final boolean isParentConfig, @NonNull final DefinitionNames definitionNames,
            final SchemasStream.EntityType entityType) {
        this.value = requireNonNull(value);
        this.title = requireNonNull(title);
        this.type = requireNonNull(type);
        this.stack = requireNonNull(context.copy());
        this.parentName = requireNonNull(parentName);
        this.isParentConfig = isParentConfig;
        this.definitionNames = definitionNames;
        this.entityType = entityType;
        this.discriminator = requireNonNullElse(discriminator, "");
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
        final var reference = reference();
        if (reference != null) {
            generator.writeStringField("$ref", reference);
        }
        generateEnum(generator);
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
        // FIXME: Ensure the method returns null if the description is not available.
        return value.getDescription()
            .orElse(value instanceof InputSchemaNode || value instanceof OutputSchemaNode ? null : "");
    }

    private @Nullable String reference() {
        return null;
    }

    private void generateEnum(final @NonNull JsonGenerator generator) throws IOException {
        // No-op
    }

    private void generateRequired(final @NonNull JsonGenerator generator, final List<String> required)
            throws IOException {
        if (!required.isEmpty()) {
            generator.writeArrayFieldStart("required");
            for (final var req : required) {
                generator.writeString(req);
            }
            generator.writeEndArray();
        }
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
        final var required = new ArrayList<String>();
        generator.writeObjectFieldStart("properties");
        stack.enterSchemaTree(value.getQName());
        switch (entityType) {
            case RPC:
                for (final var childNode : ((ContainerLike) value).getChildNodes()) {
                    new PropertyEntity(childNode, generator, stack, required, parentName, isParentConfig,
                        definitionNames);
                }
                break;
            default:
                final var childrenNames = new ArrayList<String>();
                for (final var childNode : ((DataNodeContainer) value).getChildNodes()) {
                    if (shouldBeAddedAsProperty(childNode)) {
                        if (!childrenNames.contains(childNode.getQName().getLocalName())) {
                            childrenNames.add(childNode.getQName().getLocalName());
                            new PropertyEntity(childNode, generator, stack, required, parentName + "_"
                                + value.getQName().getLocalName(), ((DataSchemaNode) value).isConfiguration(),
                                definitionNames);
                        }
                    }
                }
        }
        stack.exit();
        generator.writeEndObject();
        generateRequired(generator, required);
    }

    private boolean shouldBeAddedAsProperty(final DataSchemaNode childNode) {
        if (childNode instanceof ContainerSchemaNode) {
            return childNode.isConfiguration() || isSchemaNodeMandatory(childNode)
                || !((DataSchemaNode) value).isConfiguration();
        }
        return childNode.isConfiguration() || !((DataSchemaNode) value).isConfiguration();
    }

    private void generateXml(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("xml");
        generator.writeStringField("name", value.getQName().getLocalName());
        generator.writeStringField("namespace", value.getQName().getNamespace().toString());
        generator.writeEndObject();
    }
}

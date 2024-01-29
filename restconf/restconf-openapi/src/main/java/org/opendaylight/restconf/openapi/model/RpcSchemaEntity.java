/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.ArrayList;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Archetype for a Schema.
 */
public final class RpcSchemaEntity extends SchemaEntity {

    public RpcSchemaEntity(final @NonNull SchemaNode value, final @NonNull String title, final String discriminator,
            @NonNull final String type, @NonNull final SchemaInferenceStack context, final String parentName,
            final boolean isParentConfig, @NonNull final DefinitionNames definitionNames) {
        super(value, title, discriminator, type, context, parentName, isParentConfig, definitionNames);
    }

    @Override
    void generateProperties(final @NonNull JsonGenerator generator) throws IOException {
        final var required = new ArrayList<String>();
        generator.writeObjectFieldStart("properties");
        stack().enterSchemaTree(value().getQName());
        for (final var childNode : ((ContainerLike) value()).getChildNodes()) {
            new PropertyEntity(childNode, generator, stack(), required, parentName(), isParentConfig(),
                definitionNames());
        }
        stack().exit();
        generator.writeEndObject();
        generateRequired(generator, required);
    }
}

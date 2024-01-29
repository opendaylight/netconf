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
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

public final class RpcSchemaEntity extends SchemaEntity {

    public RpcSchemaEntity(final @NonNull SchemaNode value, final @NonNull String title,
            final @Nullable String discriminator, final @NonNull String type,
            final @NonNull SchemaInferenceStack context, final @NonNull String parentName, final boolean isParentConfig,
            final @NonNull DefinitionNames definitionNames) {
        super(value, title, discriminator, type, context, parentName, isParentConfig, definitionNames);
    }

    @Override
    void generateProperties(final @NonNull JsonGenerator generator, final @NonNull List<String> required)
            throws IOException {
        for (final var childNode : ((ContainerLike) value()).getChildNodes()) {
            new PropertyEntity(childNode, generator, stack(), required, parentName(), isParentConfig(),
                definitionNames());
        }
    }
}

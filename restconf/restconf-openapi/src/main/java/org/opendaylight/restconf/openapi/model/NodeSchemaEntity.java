/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static org.opendaylight.restconf.openapi.model.PropertyEntity.isSchemaNodeMandatory;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

public final class NodeSchemaEntity extends SchemaEntity {

    public NodeSchemaEntity(final @NonNull SchemaNode value, final @NonNull String title,
            final @Nullable String discriminator, final @NonNull String type,
            final @NonNull SchemaInferenceStack context, final @NonNull String parentName, final boolean isParentConfig,
            final @NonNull DefinitionNames definitionNames) {
        super(value, title, discriminator, type, context, parentName, isParentConfig, definitionNames);
    }

    @Override
    void generateProperties(final @NonNull JsonGenerator generator, final @NonNull List<String> required)
            throws IOException {
        final var childNodes = new HashMap<String, DataSchemaNode>();
        for (final var childNode : ((DataNodeContainer) value()).getChildNodes()) {
            childNodes.put(childNode.getQName().getLocalName(), childNode);
        }
        final boolean isValueConfig = ((DataSchemaNode) value()).isConfiguration();
        for (final var childNode : childNodes.values()) {
            if (shouldBeAddedAsProperty(childNode, isValueConfig)) {
                new PropertyEntity(childNode, generator, stack(), required, parentName() + "_"
                    + value().getQName().getLocalName(), isValueConfig, definitionNames());
            }
        }
    }

    private static boolean shouldBeAddedAsProperty(final DataSchemaNode childNode, final boolean isValueConfig) {
        final boolean isChildNodeConfig = childNode.isConfiguration();
        if (childNode instanceof ContainerSchemaNode) {
            return isChildNodeConfig || isSchemaNodeMandatory(childNode) || !isValueConfig;
        }
        return isChildNodeConfig || !isValueConfig;
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.schema.context;

import java.util.Collection;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Util class for finding {@link DataSchemaNode}.
 *
 */
public final class RestconfSchemaUtil {

    private RestconfSchemaUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Find child of {@link SchemaNode} in {@link Collection} by {@link String}
     * schema node name.
     *
     * @param <T>
     *             child of SchemaNode
     * @param collection
     *             child of node
     * @param schemaNodeName
     *             schema node name
     * @return {@link SchemaNode}
     */
    public static <T extends SchemaNode> T findSchemaNodeInCollection(final Collection<T> collection,
            final String schemaNodeName) {
        for (final T child : collection) {
            if (child.getQName().getLocalName().equals(schemaNodeName)) {
                return child;
            }
        }
        throw new RestconfDocumentedException("Schema node " + schemaNodeName + " does not exist in module.",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

}

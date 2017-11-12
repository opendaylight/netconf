/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.impl;

import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.RestconfService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class RestconfImpl implements RestconfService {

    private SchemaContextHandler schemaContextHandler;

    public RestconfImpl(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    public NormalizedNodeContext getLibraryVersion() {
        final SchemaContext context = this.schemaContextHandler.get();
        SchemaNode schemaNode = null;
        for (final GroupingDefinition groupingDefinition : context
                .findModuleByNamespaceAndRevision(RestconfModule.URI_MODULE, RestconfModule.DATE).getGroupings()) {
            if (groupingDefinition.getQName().equals(RestconfModule.RESTCONF_GROUPING_QNAME)) {
                schemaNode = ((ContainerSchemaNode) groupingDefinition
                        .getDataChildByName(RestconfModule.RESTCONF_CONTAINER_QNAME))
                                .getDataChildByName(RestconfModule.LIB_VER_LEAF_QNAME);
            }
        }
        final YangInstanceIdentifier yangIId = YangInstanceIdentifier.of(
                QName.create(RestconfModule.NAME, RestconfModule.REVISION, RestconfModule.LIB_VER_LEAF_SCHEMA_NODE));
        final InstanceIdentifierContext<? extends SchemaNode> iid =
                new InstanceIdentifierContext<SchemaNode>(yangIId, schemaNode, null, context);
        final NormalizedNode<?, ?> data =
                Builders.leafBuilder((LeafSchemaNode) schemaNode).withValue(IetfYangLibrary.REVISION).build();
        return new NormalizedNodeContext(iid, data);
    }

    public void updateHandlers(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    public void updateHandlers(final Object... handlers) {
        for (final Object object : handlers) {
            if (object instanceof SchemaContextHandler) {
                schemaContextHandler = (SchemaContextHandler) object;
            }
        }
    }
}

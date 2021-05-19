/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.Path;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfService;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

@Path("/")
public class RestconfImpl implements RestconfService {
    private final SchemaContextHandler schemaContextHandler;

    public RestconfImpl(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
    }

    @Override
    public NormalizedNodeContext getLibraryVersion() {
        final EffectiveModelContext context = this.schemaContextHandler.get();
        SchemaNode schemaNode = null;
        for (final GroupingDefinition groupingDefinition : context
                .findModule(RestconfModule.IETF_RESTCONF_QNAME.getModule()).get().getGroupings()) {
            if (groupingDefinition.getQName().equals(RestconfModule.RESTCONF_GROUPING_QNAME)) {
                schemaNode = ((ContainerSchemaNode) groupingDefinition
                        .getDataChildByName(RestconfModule.RESTCONF_CONTAINER_QNAME))
                                .getDataChildByName(RestconfModule.LIB_VER_LEAF_QNAME);
            }
        }
        return new NormalizedNodeContext(new InstanceIdentifierContext<>(
            YangInstanceIdentifier.of(RestconfModule.LIB_VER_LEAF_QNAME), schemaNode, null, context),
            ImmutableNodes.leafNode(RestconfModule.LIB_VER_LEAF_QNAME, IetfYangLibrary.REVISION.toString()));
    }
}

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
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.Restconf;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;

@Path("/")
public class RestconfImpl implements RestconfService {
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();

    private final SchemaContextHandler schemaContextHandler;

    public RestconfImpl(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
    }

    @Override
    public NormalizedNodeContext getLibraryVersion() {
        final EffectiveModelContext context = schemaContextHandler.get();

        // FIXME: why are we going through a grouping here?!
        final GroupingDefinition grouping = context
            .findModule(Restconf.QNAME.getModule())
            .orElseThrow(() -> new IllegalStateException("Failed to find restcibf module"))
            .getGroupings().stream()
            .filter(grp -> Restconf.QNAME.equals(grp.getQName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Failed to find restconf grouping"));

        final LeafSchemaNode schemaNode =
            (LeafSchemaNode) ((ContainerSchemaNode) grouping.getDataChildByName(Restconf.QNAME))
            .getDataChildByName(YANG_LIBRARY_VERSION);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(
            YangInstanceIdentifier.of(YANG_LIBRARY_VERSION), schemaNode, null, context),
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, IetfYangLibrary.REVISION.toString()));
    }
}

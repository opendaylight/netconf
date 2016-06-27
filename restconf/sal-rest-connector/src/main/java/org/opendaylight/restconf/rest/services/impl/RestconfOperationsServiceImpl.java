/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import com.google.common.base.Preconditions;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.rest.services.api.RestconfOperationsService;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Implementation of {@link RestconfOperationsService}
 *
 */
public class RestconfOperationsServiceImpl implements RestconfOperationsService {

    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointServiceHandler domMountPointServiceHandler;

    /**
     * Set {@link SchemaContextHandler} for getting actual {@link SchemaContext}
     *
     * @param schemaContextHandler
     *            - handling schema context
     * @param domMountPointServiceHandler
     *            - handling dom mount point service
     */
    public RestconfOperationsServiceImpl(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler) {
        this.schemaContextHandler = schemaContextHandler;
        this.domMountPointServiceHandler = domMountPointServiceHandler;
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        final SchemaContextRef ref = new SchemaContextRef(this.schemaContextHandler.get());
        final Set<Module> modules = ref.getModules();
        return getOperationsFromModules(modules, ref, null);
    }

    private static NormalizedNodeContext getOperationsFromModules(final Set<Module> modules, final SchemaContextRef ref, final DOMMountPoint mountPoint) {
        Module operationsModule = null;
        for (final Module module : modules) {
            if (module.getQNameModule().getNamespace().toString().equals("list:operations")
                    && module.getQNameModule().getFormattedRevision().equals("2016-06-28")) {
                operationsModule = module;
            }
        }

        Preconditions.checkNotNull(operationsModule);
        final DataSchemaNode listOperations = RestconfSchemaUtil
                .findSchemaNodeInCollection(operationsModule.getChildNodes(), "operations");
        final DataSchemaNode listNode = RestconfSchemaUtil.findSchemaNodeInCollection(((ListSchemaNode)listOperations).getChildNodes(), "operation");
        final CollectionNodeBuilder<MapEntryNode, MapNode> mapBuilder = Builders
                .mapBuilder((ListSchemaNode) listOperations);

        for (final Module module : modules) {
            for (final RpcDefinition rpc : module.getRpcs()) {
                final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder = Builders
                        .mapEntryBuilder((ListSchemaNode) listOperations);
                mapEntryBuilder.withChild(Builders
                        .leafBuilder((LeafSchemaNode) listNode)
                        .withValue(module.getName() + ":" + rpc.getQName().getLocalName()).build());
                mapBuilder.withChild(mapEntryBuilder.build());
            }
        }
        return new NormalizedNodeContext(
                new InstanceIdentifierContext<SchemaNode>(null, listOperations, mountPoint, ref.get()),
                mapBuilder.build());
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        final SchemaContextRef ref = new SchemaContextRef(this.schemaContextHandler.get());
        Set<Module> modules = null;
        DOMMountPoint mountPoint = null;
        if (identifier.contains(ControllerContext.MOUNT)) {
            final InstanceIdentifierContext<?> mountPointIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
                    this.schemaContextHandler.get());
            mountPoint = mountPointIdentifier.getMountPoint();
            modules = ref.getModules(mountPoint);
        } else {
            final QName qnameOfModule = ParserIdentifier.makeQNameFromIdentifier(identifier);
            modules = new HashSet<>();
            modules.add(ref.findModuleByNameAndRevision(qnameOfModule.getLocalName(), qnameOfModule.getRevision()));
            Date date;
            try {
                date = SimpleDateFormatUtil.getRevisionFormat().parse("2016-06-28");
            } catch (final ParseException e) {
                throw new RestconfDocumentedException("Bad format of revision");
            }
            modules.add(ref.findModuleByNameAndRevision("list-operations", date));
        }

        return getOperationsFromModules(modules, ref, mountPoint);
    }

}

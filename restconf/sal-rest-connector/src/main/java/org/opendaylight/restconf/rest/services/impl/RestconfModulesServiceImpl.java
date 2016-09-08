/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Draft16;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.rest.services.api.RestconfModulesService;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfModulesService}
 */
public class RestconfModulesServiceImpl implements RestconfModulesService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfModulesServiceImpl.class);
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
    public RestconfModulesServiceImpl(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler) {
        this.schemaContextHandler = schemaContextHandler;
        this.domMountPointServiceHandler = domMountPointServiceHandler;
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        return getModules(schemaContextRef.getModules(), schemaContextRef, null);
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        if (!identifier.contains(RestconfConstants.MOUNT)) {
            final String errMsg = "URI has bad format. If modules behind mount point should be showed,"
                    + " URI has to end with " + RestconfConstants.MOUNT;
            LOG.debug(errMsg + " for " + identifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        final InstanceIdentifierContext<?> mountPointIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
                schemaContextRef.get());
        final DOMMountPointService domMointPointService = this.domMountPointServiceHandler.get();
        final DOMMountPoint mountPoint = domMointPointService
                .getMountPoint(mountPointIdentifier.getInstanceIdentifier()).get();
        return getModules(mountPoint.getSchemaContext().getModules(), schemaContextRef, mountPoint);
    }


    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        final QName moduleQname = ParserIdentifier.makeQNameFromIdentifier(identifier);
        Module module = null;
        DOMMountPoint mountPoint = null;
        if (identifier.contains(RestconfConstants.MOUNT)) {
            final InstanceIdentifierContext<?> point = ParserIdentifier.toInstanceIdentifier(identifier,
                    schemaContextRef.get());
            final DOMMountPointService domMointPointService = this.domMountPointServiceHandler.get();
            mountPoint = domMointPointService.getMountPoint(point.getInstanceIdentifier()).get();
            module = schemaContextRef.findModuleInMountPointByQName(mountPoint, moduleQname);
        } else {
            module = schemaContextRef.findModuleByQName(moduleQname);
        }

        if (module == null) {
            final String errMsg = "Module with name '" + moduleQname.getLocalName() + "' and revision '"
                    + moduleQname.getRevision() + "' was not found.";
            LOG.debug(errMsg);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
        }

        final Set<Module> modules = Collections.singleton(module);
        final MapNode moduleMap = RestconfMappingNodeUtil
                .restconfMappingNode(schemaContextRef.getRestconfModule(), modules);
        final DataSchemaNode moduleSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(
                schemaContextRef.getRestconfModule(), Draft16.RestconfModule.MODULE_LIST_SCHEMA_NODE);
        Preconditions.checkState(moduleSchemaNode instanceof ListSchemaNode);
        if (mountPoint == null) {
            return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, moduleSchemaNode, mountPoint, schemaContextRef.get()), moduleMap);
        } else {
            return new NormalizedNodeContext(
                    new InstanceIdentifierContext<>(null, moduleSchemaNode, mountPoint, mountPoint.getSchemaContext()),
                    moduleMap);
        }
    }

    /**
     * Get {@link NormalizedNodeContext} from set of modules. Used by
     * {@link #getModules(UriInfo)} and {@link #getModules(String, UriInfo)}
     *
     * @param modules
     *            - all modules
     * @param schemaContextRef
     *            - schema context reference
     * @param mountPoint
     *            - mount point
     * @return {@link NormalizedNodeContext}
     */
    private NormalizedNodeContext getModules(final Set<Module> modules, final SchemaContextRef schemaContextRef,
            final DOMMountPoint mountPoint) {
        final Module restconfModule = schemaContextRef.getRestconfModule();
        Preconditions.checkNotNull(restconfModule);

        final MapNode mapNodes = RestconfMappingNodeUtil.restconfMappingNode(restconfModule, modules);
        final DataSchemaNode schemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft16.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(schemaNode instanceof ContainerSchemaNode);
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> modulContainerSchemaNodeBuilder = Builders
                .containerBuilder((ContainerSchemaNode) schemaNode);
        modulContainerSchemaNodeBuilder.withChild(mapNodes);
        if (mountPoint == null) {
            return new NormalizedNodeContext(
                    new InstanceIdentifierContext<>(null, schemaNode, mountPoint, schemaContextRef.get()),
                    modulContainerSchemaNodeBuilder.build());
        } else {
            return new NormalizedNodeContext(
                    new InstanceIdentifierContext<>(null, schemaNode, mountPoint, mountPoint.getSchemaContext()),
                    modulContainerSchemaNodeBuilder.build());
        }
    }
}

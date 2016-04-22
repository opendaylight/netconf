/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
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

    /**
     * Set {@link SchemaContextHandler} for getting actual {@link SchemaContext}
     * 
     * @param schemaContextHandler
     *            - handling schema context
     */
    public RestconfModulesServiceImpl(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
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
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        final DOMMountPoint mountPoint = ParserIdentifier.toInstanceIdentifier(identifier).getMountPoint();
        return getModules(schemaContextRef.getModules(mountPoint), schemaContextRef, mountPoint);
    }


    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        final QName moduleQname = ParserIdentifier.makeQNameFromIdentifier(identifier);
        Module module = null;
        DOMMountPoint mountPoint = null;
        if (identifier.contains(RestconfConstants.MOUNT)) {
            mountPoint = ParserIdentifier.toInstanceIdentifier(identifier).getMountPoint();
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
        final DataSchemaNode moduleSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(module,
                Draft11.RestconfModule.MODULE_LIST_SCHEMA_NODE);
        Preconditions.checkState(moduleSchemaNode instanceof ListSchemaNode);
        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, moduleSchemaNode, mountPoint, schemaContextRef.get()), moduleMap);
        }

    /**
     * Get {@link NormalizedNodeContext} from set of modules. Used by
     * {@link #getModules(UriInfo)} and {@link #getModules(String, UriInfo)}
     *
     * @param modules
     * @param schemaContextRef
     * @param mountPoint
     * @return {@link NormalizedNodeContext}
     */
    private NormalizedNodeContext getModules(final Set<Module> modules, final SchemaContextRef schemaContextRef,
            final DOMMountPoint mountPoint) {
        final Module restconfModule = schemaContextRef.getRestconfModule();
        Preconditions.checkNotNull(restconfModule);

        final MapNode mapNodes = RestconfMappingNodeUtil.restconfMappingNode(restconfModule, modules);
        final DataSchemaNode schemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft11.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(schemaNode instanceof ContainerSchemaNode);
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> modulContainerSchemaNodeBuilder = Builders
                .containerBuilder((ContainerSchemaNode) schemaNode);
        modulContainerSchemaNodeBuilder.withChild(mapNodes);

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, schemaNode, mountPoint, schemaContextRef.get()),
                modulContainerSchemaNodeBuilder.build());
    }
}

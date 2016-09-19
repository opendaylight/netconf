/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.rest.services.api.RestconfOperationsService;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.EffectiveSchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfOperationsService}
 *
 */
public class RestconfOperationsServiceImpl implements RestconfOperationsService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfOperationsServiceImpl.class);

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
        return getOperations(ref.getModules(), null);
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        final Set<Module> modules;
        final DOMMountPoint mountPoint;
        final SchemaContextRef ref = new SchemaContextRef(this.schemaContextHandler.get());
        if (identifier.contains(RestconfConstants.MOUNT)) {
            final InstanceIdentifierContext<?> mountPointIdentifier = ParserIdentifier.toInstanceIdentifier(
                    identifier, ref.get(), Optional.of(this.domMountPointServiceHandler.get()));
            mountPoint = mountPointIdentifier.getMountPoint();
            modules = ref.getModules(mountPoint);

        } else {
            final String errMsg = "URI has bad format. If operations behind mount point should be showed, URI has to end with ";
            LOG.debug(errMsg + ControllerContext.MOUNT + " for " + identifier);
            throw new RestconfDocumentedException(errMsg + ControllerContext.MOUNT, ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        return getOperations(modules, mountPoint);
    }

    /**
     * Special case only for GET restconf/operations use (since moment of old
     * Yang parser and old Yang model API removal). The method is creating fake
     * schema context with fake module and fake data by use own implementations
     * of schema nodes and module.
     *
     * @param modules
     *            - set of modules for get RPCs from every module
     * @param mountPoint
     *            - mount point, if in use otherwise null
     * @return {@link NormalizedNodeContext}
     */
    private static NormalizedNodeContext getOperations(final Set<Module> modules, final DOMMountPoint mountPoint) {
        final ContainerSchemaNodeImpl fakeCont = new ContainerSchemaNodeImpl();
        final List<LeafNode<Object>> listRpcNodes = new ArrayList<>();
        for (final Module m : modules) {
            for (final RpcDefinition rpc : m.getRpcs()) {

                final LeafSchemaNode fakeLeaf = new LeafSchemaNodeImpl(fakeCont.getPath(),
                        QName.create(ModuleImpl.moduleQName, m.getName() + ":" + rpc.getQName().getLocalName()));
                fakeCont.addNodeChild(fakeLeaf);
                listRpcNodes.add(Builders.leafBuilder(fakeLeaf).build());
            }
        }
        final ContainerSchemaNode fakeContSchNode = fakeCont;
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> containerBuilder = Builders
                .containerBuilder(fakeContSchNode);

        for (final LeafNode<Object> rpcNode : listRpcNodes) {
            containerBuilder.withChild(rpcNode);
        }

        final Module fakeModule = new ModuleImpl(fakeContSchNode);

        final Set<Module> fakeModules = new HashSet<>();
        fakeModules.add(fakeModule);
        final SchemaContext fakeSchemaCtx = EffectiveSchemaContext.resolveSchemaContext(fakeModules);
        final InstanceIdentifierContext<ContainerSchemaNode> instanceIdentifierContext = new InstanceIdentifierContext<>(
                null, fakeContSchNode, mountPoint, fakeSchemaCtx);
        return new NormalizedNodeContext(instanceIdentifierContext, containerBuilder.build());
    }

}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.impl;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.services.simple.api.RestconfOperationsService;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.util.SimpleSchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfOperationsService}.
 *
 */
public class RestconfOperationsServiceImpl implements RestconfOperationsService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfOperationsServiceImpl.class);

    private SchemaContextHandler schemaContextHandler;
    private DOMMountPointServiceHandler domMountPointServiceHandler;

    /**
     * Set {@link SchemaContextHandler} for getting actual {@link SchemaContext}.
     *
     * @param schemaContextHandler
     *             handling schema context
     * @param domMountPointServiceHandler
     *             handling dom mount point service
     */
    public RestconfOperationsServiceImpl(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler domMountPointServiceHandler) {
        this.schemaContextHandler = schemaContextHandler;
        this.domMountPointServiceHandler = domMountPointServiceHandler;
    }

    @Override
    public void updateHandlers(final Object... handlers) {
        for (final Object object : handlers) {
            if (object instanceof SchemaContextHandler) {
                schemaContextHandler = (SchemaContextHandler) object;
            } else if (object instanceof DOMMountPointServiceHandler) {
                domMountPointServiceHandler = (DOMMountPointServiceHandler) object;
            }
        }
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
            final InstanceIdentifierContext<?> mountPointIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
                    ref.get(), Optional.of(this.domMountPointServiceHandler.get()));
            mountPoint = mountPointIdentifier.getMountPoint();
            modules = ref.getModules(mountPoint);
        } else {
            final String errMsg =
                    "URI has bad format. If operations behind mount point should be showed, URI has to end with ";
            LOG.debug(errMsg + RestconfConstants.MOUNT + " for " + identifier);
            throw new RestconfDocumentedException(errMsg + RestconfConstants.MOUNT, ErrorType.PROTOCOL,
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
     *             set of modules for get RPCs from every module
     * @param mountPoint
     *             mount point, if in use otherwise null
     * @return {@link NormalizedNodeContext}
     */
    private static NormalizedNodeContext getOperations(final Set<Module> modules, final DOMMountPoint mountPoint) {
        final Collection<Module> neededModules = new ArrayList<>(modules.size());
        final ArrayList<LeafSchemaNode> fakeRpcSchema = new ArrayList<>();

        for (final Module m : modules) {
            final Set<RpcDefinition> rpcs = m.getRpcs();
            if (!rpcs.isEmpty()) {
                neededModules.add(m);

                fakeRpcSchema.ensureCapacity(fakeRpcSchema.size() + rpcs.size());
                rpcs.forEach(rpc -> fakeRpcSchema.add(new FakeLeafSchemaNode(rpc.getQName())));
            }
        }

        final ContainerSchemaNode fakeCont = new FakeContainerSchemaNode(fakeRpcSchema);
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> containerBuilder =
                Builders.containerBuilder(fakeCont);

        for (final LeafSchemaNode leaf : fakeRpcSchema) {
            containerBuilder.withChild(Builders.leafBuilder(leaf).build());
        }

        final Collection<Module> fakeModules = new ArrayList<>(neededModules.size() + 1);
        neededModules.forEach(imp -> fakeModules.add(new FakeImportedModule(imp)));
        fakeModules.add(new FakeRestconfModule(neededModules, fakeCont));

        final SchemaContext fakeSchemaCtx = SimpleSchemaContext.forModules(ImmutableSet.copyOf(fakeModules));
        final InstanceIdentifierContext<ContainerSchemaNode> instanceIdentifierContext =
                new InstanceIdentifierContext<>(null, fakeCont, mountPoint, fakeSchemaCtx);
        return new NormalizedNodeContext(instanceIdentifierContext, containerBuilder.build());
    }

}

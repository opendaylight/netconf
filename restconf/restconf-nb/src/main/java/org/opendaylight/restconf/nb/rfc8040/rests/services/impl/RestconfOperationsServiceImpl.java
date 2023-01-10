/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfOperationsService;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfOperationsService}.
 */
@Path("/")
public class RestconfOperationsServiceImpl implements RestconfOperationsService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfOperationsServiceImpl.class);

    private final DatabindProvider databindProvider;
    private final DOMMountPointService mountPointService;

    /**
     * Set {@link DatabindProvider} for getting actual {@link EffectiveModelContext}.
     *
     * @param databindProvider a {@link DatabindProvider}
     * @param mountPointService a {@link DOMMountPointService}
     */
    public RestconfOperationsServiceImpl(final DatabindProvider databindProvider,
            final DOMMountPointService mountPointService) {
        this.databindProvider = requireNonNull(databindProvider);
        this.mountPointService = requireNonNull(mountPointService);
    }

    @Override
    public String getOperationsJSON() {
        return OperationsContent.JSON.bodyFor(databindProvider.currentContext().modelContext());
    }

    @Override
    public String getOperationJSONByIdentifier(String identifier) {
        return OperationsContent.JSON.bodyFor(databindProvider.currentContext().modelContext(), identifier);
    }

    @Override
    public String getOperationsXML() {
        return OperationsContent.XML.bodyFor(databindProvider.currentContext().modelContext());
    }

    @Override
    public String getOperationXMLByIdentifier(String identifier) {
        return OperationsContent.XML.bodyFor(databindProvider.currentContext().modelContext(), identifier);
    }

    /*@Override
    public NormalizedNodePayload getOperations(final String identifier, final UriInfo uriInfo) {
        if (!identifier.contains(RestconfConstants.MOUNT)) {
            final var errMsg = """
                    URI has bad format. If operations behind mount point should be showed, URI has to end with %s.
                    """.formatted(RestconfConstants.MOUNT);
            LOG.debug("{} for {}", errMsg, identifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final InstanceIdentifierContext mountPointIdentifier = ParserIdentifier.toInstanceIdentifier(identifier,
            databindProvider.currentContext().modelContext(), Optional.of(mountPointService));
        final DOMMountPoint mountPoint = mountPointIdentifier.getMountPoint();
        final var entry = contextForModelContext(modelContext(mountPoint), mountPoint);
        return NormalizedNodePayload.of(entry.getKey(), entry.getValue());
    }*/

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }

    // FIXME: remove this method and everything it uses
    @Deprecated(forRemoval = true, since = "4.0.0")
    private static @NonNull Entry<InstanceIdentifierContext, ContainerNode> contextForModelContext(
            final @NonNull EffectiveModelContext context, final @Nullable DOMMountPoint mountPoint) {
        // Determine which modules we need and construct leaf schemas to correspond to all RPC definitions
        final Collection<Module> modules = new ArrayList<>();
        final ArrayList<OperationsLeafSchemaNode> rpcLeafSchemas = new ArrayList<>();
        for (final Module m : context.getModules()) {
            final Collection<? extends RpcDefinition> rpcs = m.getRpcs();
            if (!rpcs.isEmpty()) {
                modules.add(new OperationsImportedModule(m));
                rpcLeafSchemas.ensureCapacity(rpcLeafSchemas.size() + rpcs.size());
                for (RpcDefinition rpc : rpcs) {
                    rpcLeafSchemas.add(new OperationsLeafSchemaNode(rpc));
                }
            }
        }

        // Now generate a module for RESTCONF so that operations contain what they need
        final OperationsContainerSchemaNode operatationsSchema = new OperationsContainerSchemaNode(rpcLeafSchemas);
        modules.add(new OperationsRestconfModule(operatationsSchema));

        // Now build the operations container and combine it with the context
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> operationsBuilder = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(OperationsContainerSchemaNode.QNAME));
        for (final OperationsLeafSchemaNode leaf : rpcLeafSchemas) {
            operationsBuilder.withChild(ImmutableNodes.leafNode(leaf.getQName(), Empty.value()));
        }

        final var opContext = new OperationsEffectiveModuleContext(ImmutableSet.copyOf(modules));
        final var stack = SchemaInferenceStack.of(opContext);
        stack.enterSchemaTree(operatationsSchema.getQName());

        return Map.entry(InstanceIdentifierContext.ofStack(stack, mountPoint), operationsBuilder.build());
    }

}

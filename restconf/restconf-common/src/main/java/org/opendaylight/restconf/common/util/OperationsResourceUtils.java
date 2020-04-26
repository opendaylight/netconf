/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.util.SimpleSchemaContext;


public final class OperationsResourceUtils {
    private OperationsResourceUtils() {
        // Hidden on purpose
    }

    public static @NonNull NormalizedNodeContext contextForModelContext(final @NonNull SchemaContext context,
            final @Nullable DOMMountPoint mountPoint) {
        // Determine which modules we need and construct leaf schemas to correspond to all RPC definitions
        final Collection<Module> modules = new ArrayList<>();
        final ArrayList<OperationsLeafSchemaNode> rpcLeafSchemas = new ArrayList<>();
        for (final Module m : context.getModules()) {
            final Collection<? extends RpcDefinition> rpcs = m.getRpcs();
            if (!rpcs.isEmpty()) {
                modules.add(m);
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
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> operationsBuilder = Builders.containerBuilder();
        for (final OperationsLeafSchemaNode leaf : rpcLeafSchemas) {
            operationsBuilder.withChild(ImmutableNodes.leafNode(leaf.getQName(), Empty.getInstance()));
        }

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, operatationsSchema, mountPoint,
                SimpleSchemaContext.forModules(ImmutableSet.copyOf(modules))), operationsBuilder.build());
    }
}

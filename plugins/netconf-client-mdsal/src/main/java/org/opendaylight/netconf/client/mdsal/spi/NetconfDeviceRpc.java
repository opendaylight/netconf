/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;

/**
 * Invokes RPC by sending netconf message via listener. Also transforms result from NetconfMessage to
 * {@link ContainerNode}.
 */
public final class NetconfDeviceRpc implements Rpcs.Normalized {
    private final @NonNull NetconfDeviceDOMRpcService domRpcService;
    private final @NonNull EffectiveModelContext modelContext;
    private final Map<QNameModule, ModuleEffectiveStatement> moduleEffectiveStatementMap;

    public NetconfDeviceRpc(final EffectiveModelContext modelContext, final RemoteDeviceCommunicator communicator,
            final RpcTransformer<ContainerNode, DOMRpcResult> transformer) {
        domRpcService = new NetconfDeviceDOMRpcService(modelContext, communicator, transformer);
        this.modelContext = modelContext;
        this.moduleEffectiveStatementMap = Map.copyOf(modelContext.getModuleStatements());
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
        if (moduleEffectiveStatementMap.containsKey(type.getModule())) {
            return domRpcService().invokeRpc(type, input);
        } else {
            final var module = modelContext.findModules(type.getModule().namespace()).stream()
                .max(Comparator.comparing((Module m) -> m.getRevision().orElse(null),
                    Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
            final var patchedModule = module.getQNameModule();
            final var patchedType = QName.create(patchedModule, type.getLocalName());
            final var patchedInput = (ContainerNode) rewriteNodeTree(input,
                qn -> qn.getModule().equals(type.getModule())
                    ? QName.create(patchedModule, qn.getLocalName())
                    : qn);

            return domRpcService.invokeRpc(patchedType, patchedInput);
        }
    }

    private static NormalizedNode rewriteNodeTree(final NormalizedNode node, final Function<QName, QName> mapper) {

        var newId = new YangInstanceIdentifier.NodeIdentifier(mapper.apply((node.name()).getNodeType()));

        switch (node) {
            case ContainerNode container -> {
                var builder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(newId);
                for (var child : container.body()) {
                    builder.withChild((DataContainerChild) rewriteNodeTree(child, mapper));
                }
                return builder.build();
            }
            case ChoiceNode choice -> {
                var builder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(newId);
                for (var child : choice.body()) {
                    builder.withChild((DataContainerChild) rewriteNodeTree(child, mapper));
                }
                return builder.build();
            }
            case LeafNode<?> leaf -> {
                return ImmutableNodes.leafNode(newId, leaf.body());
            }
            case AnyxmlNode any -> {
                return newId.equals(any.name().getNodeType()) ? node
                    : ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
                    .withNodeIdentifier(newId)
                    .withValue((DOMSource) any.body())
                    .build();
            }
            case MapEntryNode entry -> {
                final var oldId = entry.name();
                final var newEntryId = oldId.getNodeType().equals(newId) ? oldId
                    : YangInstanceIdentifier.NodeIdentifierWithPredicates.of(newId.getNodeType(), oldId.asMap());

                final var builder = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(newEntryId);
                entry.body().forEach(child ->
                    builder.withChild((DataContainerChild) rewriteNodeTree(child, mapper)));
                return builder.build();
            }
            default -> {
                return node;
            }
        }
    }

    @Override
    public DOMRpcService domRpcService() {
        return domRpcService;
    }
}

/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
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
        }
        final var module = modelContext.findModules(type.getModule().namespace()).stream()
            .max(Comparator.comparing((Module m) -> m.getRevision().orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder())))
            .orElseThrow(() -> new IllegalStateException("EffectiveModelContext does not contain model: "
                + type.getModule()));
        final var patchedModule = module.getQNameModule();
        final var patchedType = QName.create(patchedModule, type.getLocalName());
        final Function<QName, QName> toLatest = qn -> qn.getModule().equals(type.getModule())
                ? QName.create(patchedModule, qn.getLocalName())
                : qn;
        final Function<QName, QName> toOriginal = qn -> qn.getModule().equals(patchedModule)
                ? QName.create(type.getModule(), qn.getLocalName())
                : qn;
        final var patchedInput = (ContainerNode) rewriteNodeTree(input, toLatest);

        final var future = domRpcService.invokeRpc(patchedType, patchedInput);
        // Map result back if we patched
        if (!patchedType.equals(type)) {
            return Futures.transform(future, result -> {
                final var value = result.value();
                final var remapped = value != null ? (ContainerNode) rewriteNodeTree(value, toOriginal) : null;
                return new DefaultDOMRpcResult(remapped, result.errors());
            }, MoreExecutors.directExecutor());
        }
        return future;
    }

    private static NormalizedNode rewriteNodeTree(final NormalizedNode node, final Function<QName, QName> mapper) {
        final var oldQName = node.name().getNodeType();
        final var newQName = mapper.apply(oldQName);
        final var newId = YangInstanceIdentifier.NodeIdentifier.create(newQName);
        switch (node) {
            case ContainerNode cont -> {
                final var builder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(newId);
                cont.body().forEach(ch ->
                    builder.withChild((DataContainerChild) rewriteNodeTree(ch, mapper)));
                return builder.build();
            }
            case ChoiceNode choice -> {
                final var builder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(newId);
                choice.body().forEach(ch ->
                    builder.withChild((DataContainerChild) rewriteNodeTree(ch, mapper)));
                return builder.build();
            }
            case LeafNode<?> leaf -> {
                if (leaf.name().getNodeType().equals(newQName)) {
                    return node;
                }
                return ImmutableNodes.leafNode(newId, leaf.body());
            }
            case AnyxmlNode any -> {
                if (any.name().getNodeType().equals(newQName)) {
                    return node;
                }
                return ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
                    .withNodeIdentifier(newId)
                    .withValue((DOMSource) any.body())
                    .build();
            }
            case MapEntryNode entry -> {
                final var oldId = entry.name();
                final var patchedId = oldId.getNodeType().equals(newQName)
                    ? oldId
                    : YangInstanceIdentifier.NodeIdentifierWithPredicates.of(newQName, oldId.asMap());
                final var builder = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(patchedId);
                entry.body().forEach(ch ->
                    builder.withChild((DataContainerChild) rewriteNodeTree(ch, mapper)));
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

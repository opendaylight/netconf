/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
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
        this.modelContext = requireNonNull(modelContext);
        moduleEffectiveStatementMap = Map.copyOf(modelContext.getModuleStatements());
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Implementation note:</b> If the {@code EffectiveModelContext} lacks the exact
     * module revision referenced by {@code type.getModule()} (e.g., NETCONF base 2011-06-01),
     * this implementation resolves the latest available module for the same namespace
     * (e.g., 2013-09-29), rewrites the outbound RPC {@code QName} and any NETCONF-base
     * node identifiers inside {@code input} to that revision, invokes the RPC, and then
     * rewrites the returned normalized output back to the caller’s original module revision.
     *
     * <p>If no module exists for the RPC’s namespace, an {@link IllegalStateException}
     * is thrown with a descriptive message.
     */
    @Override
    public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
        if (moduleEffectiveStatementMap.containsKey(type.getModule())) {
            return domRpcService().invokeRpc(type, input);
        }
        final var module = modelContext.findModules(type.getModule().namespace()).stream()
            .filter(m -> m.getRevision().isPresent())
            .max(Comparator.comparing(m -> m.getRevision().orElseThrow()))
            .orElseThrow(
                () -> new IllegalStateException("EffectiveModelContext does not contain model: " + type.getModule()));
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
        final var holder = new NormalizationResultHolder();
        try (var base = ImmutableNormalizedNodeStreamWriter.from(holder);
             var nodeStreamWriter = new RemappingNormalizedNodeStreamWriter(base, mapper);
             var normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(nodeStreamWriter)) {
            normalizedNodeWriter.write(node);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite NormalizedNode for " + node.name().getNodeType(), e);
        }
        return holder.getResult().data();
    }

    @Override
    public DOMRpcService domRpcService() {
        return domRpcService;
    }
}

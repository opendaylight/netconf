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
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
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
            final var patchedType = QName.create(module.getQNameModule(), type.getLocalName());
            return domRpcService.invokeRpc(patchedType, input);
        }
    }

    @Override
    public DOMRpcService domRpcService() {
        return domRpcService;
    }
}

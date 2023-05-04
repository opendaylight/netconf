/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DefaultDOMRpcException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.NoOpListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Invokes RPC by sending netconf message via listener. Also transforms result from NetconfMessage to
 * {@link ContainerNode}.
 */
public final class NetconfDeviceRpc implements Rpcs.Normalized {
    private final RemoteDeviceCommunicator communicator;
    private final RpcTransformer<ContainerNode, DOMRpcResult> transformer;
    private final EffectiveModelContext modelContext;

    public NetconfDeviceRpc(final EffectiveModelContext modelContext, final RemoteDeviceCommunicator communicator,
            final RpcTransformer<ContainerNode, DOMRpcResult> transformer) {
        this.modelContext = requireNonNull(modelContext);
        this.communicator = communicator;
        this.transformer = transformer;
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<DOMRpcResult> invokeRpc(final QName type, final ContainerNode input) {
        final var delegateFuture = communicator.sendRequest(transformer.toRpcRequest(type, input), type);

        final var ret = SettableFuture.<DOMRpcResult>create();
        Futures.addCallback(delegateFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(final RpcResult<NetconfMessage> result) {
                final DOMRpcResult rpcResult;
                try {
                    rpcResult = transformer.toRpcResult(result, type);
                } catch (Exception cause) {
                    ret.setException(new DefaultDOMRpcException(
                        "Unable to parse rpc reply. type: " + type + " input: " + input, cause));
                    return;
                }

                ret.set(rpcResult);
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setException(new DOMRpcImplementationNotAvailableException(cause, "Unable to invoke rpc %s", type));
            }

        }, MoreExecutors.directExecutor());
        return ret;
    }

    @Override
    public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(final T listener) {
        listener.onRpcAvailable(Collections2.transform(modelContext.getOperations(),
            input -> DOMRpcIdentifier.create(input.getQName())));

        // NOOP, no rpcs appear and disappear in this implementation
        return NoOpListenerRegistration.of(listener);
    }
}

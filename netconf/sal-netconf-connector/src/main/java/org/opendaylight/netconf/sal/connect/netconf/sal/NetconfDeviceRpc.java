/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

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
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DefaultDOMRpcException;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.NoOpListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Invokes RPC by sending netconf message via listener. Also transforms result from NetconfMessage to CompositeNode.
 */
public final class NetconfDeviceRpc implements DOMRpcService {
    private final RemoteDeviceCommunicator<NetconfMessage> communicator;
    private final MessageTransformer<NetconfMessage> transformer;
    private final SchemaContext schemaContext;

    public NetconfDeviceRpc(final SchemaContext schemaContext,
            final RemoteDeviceCommunicator<NetconfMessage> communicator,
            final MessageTransformer<NetconfMessage> transformer) {
        this.communicator = communicator;
        this.transformer = transformer;
        this.schemaContext = requireNonNull(schemaContext);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<DOMRpcResult> invokeRpc(final QName type, final NormalizedNode<?, ?> input) {
        final ListenableFuture<RpcResult<NetconfMessage>> delegateFuture = communicator.sendRequest(
            transformer.toRpcRequest(type, input), type);

        final SettableFuture<DOMRpcResult> ret = SettableFuture.create();
        Futures.addCallback(delegateFuture, new FutureCallback<RpcResult<NetconfMessage>>() {
            @Override
            public void onSuccess(final RpcResult<NetconfMessage> result) {
                try {
                    ret.set(result.isSuccessful() ? transformer.toRpcResult(result.getResult(), type)
                            : new DefaultDOMRpcResult(result.getErrors()));
                } catch (Exception cause) {
                    ret.setException(new DefaultDOMRpcException(
                            "Unable to parse rpc reply. type: " + type + " input: " + input, cause));
                }
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
        listener.onRpcAvailable(Collections2.transform(schemaContext.getOperations(),
            input -> DOMRpcIdentifier.create(input.getQName())));

        // NOOP, no rpcs appear and disappear in this implementation
        return NoOpListenerRegistration.of(listener);
    }
}

/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcIdentifier;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Invokes RPC by sending netconf message via listener. Also transforms result from NetconfMessage to CompositeNode.
 */
public final class NetconfDeviceRpc implements DOMRpcService {

    private final RemoteDeviceCommunicator<NetconfMessage> communicator;
    private final MessageTransformer<NetconfMessage> transformer;
    private final Collection<DOMRpcIdentifier> availableRpcs;

    public NetconfDeviceRpc(final SchemaContext schemaContext,
            final RemoteDeviceCommunicator<NetconfMessage> communicator,
            final MessageTransformer<NetconfMessage> transformer) {
        this.communicator = communicator;
        this.transformer = transformer;

        availableRpcs = Collections2.transform(schemaContext.getOperations(),
            input -> DOMRpcIdentifier.create(input.getPath()));
    }

    @Nonnull
    @Override
    public CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(@Nonnull final SchemaPath type,
                                                                  @Nullable final NormalizedNode<?, ?> input) {
        final NetconfMessage message = transformer.toRpcRequest(type, input);
        final ListenableFuture<RpcResult<NetconfMessage>> delegateFutureWithPureResult =
                communicator.sendRequest(message, type.getLastComponent());

        final ListenableFuture<DOMRpcResult> transformed =
            Futures.transform(delegateFutureWithPureResult, input1 -> {
                if (input1.isSuccessful()) {
                    return transformer.toRpcResult(input1.getResult(), type);
                } else {
                    return new DefaultDOMRpcResult(input1.getErrors());
                }
            }, MoreExecutors.directExecutor());

        return Futures.makeChecked(transformed, new Function<Exception, DOMRpcException>() {
            @Nullable
            @Override
            public DOMRpcException apply(@Nullable final Exception exception) {
                return new DOMRpcImplementationNotAvailableException(exception, "Unable to invoke rpc %s", type);
            }
        });
    }

    @Nonnull
    @Override
    public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(
            @Nonnull final T listener) {

        listener.onRpcAvailable(availableRpcs);

        return new ListenerRegistration<T>() {
            @Override
            public void close() {
                // NOOP, no rpcs appear and disappear in this implementation
            }

            @Override
            public T getInstance() {
                return listener;
            }
        };
    }
}

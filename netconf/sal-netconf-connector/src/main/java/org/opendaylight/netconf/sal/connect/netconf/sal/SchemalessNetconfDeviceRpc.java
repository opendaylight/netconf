/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseRpcSchemalessTransformer;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.SchemalessMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Invokes RPC by sending netconf message via listener. Also transforms result from NetconfMessage to CompositeNode.
 */
public final class SchemalessNetconfDeviceRpc implements DOMRpcService {

    private final RemoteDeviceCommunicator<NetconfMessage> listener;
    private final BaseRpcSchemalessTransformer baseRpcTransformer;
    private final SchemalessMessageTransformer schemalessTransformer;
    private final RemoteDeviceId deviceId;

    public SchemalessNetconfDeviceRpc(final RemoteDeviceId deviceId,
                                      final RemoteDeviceCommunicator<NetconfMessage> listener,
                                      final BaseRpcSchemalessTransformer baseRpcTransformer,
                                      final SchemalessMessageTransformer messageTransformer) {
        this.deviceId = deviceId;
        this.listener = listener;
        this.baseRpcTransformer = baseRpcTransformer;
        this.schemalessTransformer = messageTransformer;
    }

    @Override
    public ListenableFuture<DOMRpcResult> invokeRpc(final QName type, final NormalizedNode<?, ?> input) {
        final MessageTransformer<NetconfMessage> transformer;
        if (input instanceof DOMSourceAnyxmlNode) {
            transformer = schemalessTransformer;
        } else if (isBaseRpc(type)) {
            transformer = baseRpcTransformer;
        } else {
            return Futures.immediateFailedFuture(new DOMRpcImplementationNotAvailableException(
                "Unable to invoke rpc %s", type));
        }
        return handleRpc(type, input, transformer);
    }

    private ListenableFuture<DOMRpcResult> handleRpc(
            final @NonNull QName type, final @NonNull NormalizedNode<?, ?> input,
            final MessageTransformer<NetconfMessage> transformer) {
        final ListenableFuture<RpcResult<NetconfMessage>> delegateFuture = listener.sendRequest(
            transformer.toRpcRequest(type, input), type);

        final SettableFuture<DOMRpcResult> ret = SettableFuture.create();
        Futures.addCallback(delegateFuture, new FutureCallback<RpcResult<NetconfMessage>>() {
            @Override
            public void onSuccess(final RpcResult<NetconfMessage> result) {
                ret.set(result.isSuccessful() ? transformer.toRpcResult(result.getResult(), type)
                        : new DefaultDOMRpcResult(result.getErrors()));
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setException(new DOMRpcImplementationNotAvailableException(cause,
                    "Unable to invoke rpc %s on device %s", type, deviceId));
            }

        }, MoreExecutors.directExecutor());
        return ret;
    }

    private static boolean isBaseRpc(final QName type) {
        return NetconfMessageTransformUtil.NETCONF_URI.equals(type.getNamespace());
    }

    @Override
    public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(final T lsnr) {
        throw new UnsupportedOperationException("Not available for netconf 1.0");
    }
}

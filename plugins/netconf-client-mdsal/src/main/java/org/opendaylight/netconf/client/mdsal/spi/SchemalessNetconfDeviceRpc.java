/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.netconf.client.mdsal.impl.BaseRpcSchemalessTransformer;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.client.mdsal.impl.SchemalessMessageTransformer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * Invokes RPC by sending netconf message via listener. Also transforms result from NetconfMessage to CompositeNode.
 */
public final class SchemalessNetconfDeviceRpc implements Rpcs.Schemaless {
    private final RemoteDeviceCommunicator listener;
    private final BaseRpcSchemalessTransformer baseRpcTransformer;
    private final SchemalessMessageTransformer schemalessTransformer;
    private final RemoteDeviceId deviceId;

    public SchemalessNetconfDeviceRpc(final RemoteDeviceId deviceId, final RemoteDeviceCommunicator listener,
            final BaseRpcSchemalessTransformer baseRpcTransformer,
            final SchemalessMessageTransformer messageTransformer) {
        this.deviceId = deviceId;
        this.listener = listener;
        this.baseRpcTransformer = baseRpcTransformer;
        schemalessTransformer = messageTransformer;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
        if (!isBaseRpc(type)) {
            throw new IllegalArgumentException("Cannot handle " + type);
        }
        return handleRpc(type, input, baseRpcTransformer);
    }

    @Override
    public ListenableFuture<? extends DOMSource> invokeRpc(final QName type, final DOMSource input) {
        return handleRpc(type, input, schemalessTransformer);
    }

    private @NonNull <I, R> ListenableFuture<R> handleRpc(final @NonNull QName type,
            final @NonNull I input, final RpcTransformer<I, R> transformer) {
        final var delegateFuture = listener.sendRequest(transformer.toRpcRequest(type, input), type);
        final var ret = SettableFuture.<R>create();
        Futures.addCallback(delegateFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(final RpcResult<NetconfMessage> result) {
                ret.set(transformer.toRpcResult(result, type));
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
}

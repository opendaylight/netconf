/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import com.google.common.base.Preconditions;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMActionServiceExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the factory for creation of {@link DOMActionService} instances that are provided by device.
 * {@link DOMActionService} is implemented using {@link MessageTransformer} that builds NETCONF RPCs and
 * transforms replied NETCONF message  to action result, and using {@link RemoteDeviceCommunicator} that is responsible
 * for sending of built RPCs to NETCONF client.
 */
public class DeviceActionFactoryImpl implements DeviceActionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceActionFactoryImpl.class);

    @Override
    public DOMActionService createDeviceAction(final MessageTransformer<NetconfMessage> messageTransformer,
            final RemoteDeviceCommunicator<NetconfMessage> listener, final SchemaContext schemaContext) {

        return new DOMActionService() {
            @Override
            public FluentFuture<? extends DOMActionResult> invokeAction(final SchemaPath schemaPath,
                    final DOMDataTreeIdentifier dataTreeIdentifier, final ContainerNode input) {
                Preconditions.checkNotNull(schemaPath);
                Preconditions.checkNotNull(dataTreeIdentifier);
                Preconditions.checkNotNull(input);

                final FluentFuture<RpcResult<NetconfMessage>> rpcResultFluentFuture = listener.sendRequest(
                        messageTransformer.toActionRequest(schemaPath, dataTreeIdentifier, input), input.getNodeType());

                return rpcResultFluentFuture.transform(netconfMessageRpcResult -> {
                    if (netconfMessageRpcResult != null) {
                        return messageTransformer.toActionResult(schemaPath, netconfMessageRpcResult.getResult());
                    } else {
                        final String message = "Failed to transform action request result to action response, "
                                + "result is null.";
                        LOG.error(message);
                        throw new IllegalStateException(message);
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public @NonNull ClassToInstanceMap<DOMActionServiceExtension> getExtensions() {
                return ImmutableClassToInstanceMap.of();
            }
        };
    }
}
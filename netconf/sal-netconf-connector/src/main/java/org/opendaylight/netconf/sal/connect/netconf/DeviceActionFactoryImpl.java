/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMActionServiceExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.nativ.netconf.communicator.RemoteDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the factory for creation of {@link DOMActionService} instances that are provided by device.
 * {@link DOMActionService} is implemented using {@link MessageTransformer} that builds NETCONF RPCs and
 * transforms replied NETCONF message  to action result, and using {@link RemoteDeviceCommunicator} that is responsible
 * for sending of built RPCs to NETCONF client.
 */
@Singleton
@Component(immediate = true, property = "type=default")
public class DeviceActionFactoryImpl implements DeviceActionFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceActionFactoryImpl.class);

    @Override
    public DOMActionService createDeviceAction(final MessageTransformer<NetconfMessage> messageTransformer,
            final RemoteDeviceCommunicator<NetconfMessage> listener, final SchemaContext schemaContext) {

        return new DOMActionService() {
            @Override
            public ListenableFuture<? extends DOMActionResult> invokeAction(final Absolute schemaPath,
                    final DOMDataTreeIdentifier dataTreeIdentifier, final ContainerNode input) {
                requireNonNull(schemaPath);
                requireNonNull(dataTreeIdentifier);
                requireNonNull(input);

                final ListenableFuture<RpcResult<NetconfMessage>> actionResultFuture = listener.sendRequest(
                        messageTransformer.toActionRequest(schemaPath, dataTreeIdentifier, input), input.getNodeType());

                return Futures.transform(actionResultFuture, netconfMessageRpcResult -> {
                    if (netconfMessageRpcResult != null) {
                        return messageTransformer.toActionResult(schemaPath, netconfMessageRpcResult.getResult());
                    } else {
                        final String message = "Missing action result of action on schema path: " + schemaPath;
                        LOG.error(message);
                        throw new IllegalStateException(message);
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public ClassToInstanceMap<DOMActionServiceExtension> getExtensions() {
                return ImmutableClassToInstanceMap.of();
            }
        };
    }
}
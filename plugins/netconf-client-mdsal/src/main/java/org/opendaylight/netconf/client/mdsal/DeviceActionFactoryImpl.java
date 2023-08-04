/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.netconf.client.mdsal.api.ActionTransformer;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the factory for creation of {@link DOMActionService} instances that are provided by device.
 * {@link DOMActionService} is implemented using {@link ActionTransformer} that builds NETCONF RPCs and
 * transforms replied NETCONF message  to action result, and using {@link RemoteDeviceCommunicator} that is responsible
 * for sending of built RPCs to NETCONF client.
 */
@Singleton
@Component(immediate = true, property = "type=default")
public class DeviceActionFactoryImpl implements DeviceActionFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceActionFactoryImpl.class);

    @Override
    public Actions.Normalized createDeviceAction(final ActionTransformer messageTransformer,
            final RemoteDeviceCommunicator listener) {
        return (schemaPath, dataTreeIdentifier, input) -> {
            requireNonNull(schemaPath);
            requireNonNull(dataTreeIdentifier);
            requireNonNull(input);

            final var actionResultFuture = listener.sendRequest(
                messageTransformer.toActionRequest(schemaPath, dataTreeIdentifier, input), input.name().getNodeType());

            return Futures.transform(actionResultFuture, netconfMessageRpcResult -> {
                if (netconfMessageRpcResult != null) {
                    return messageTransformer.toActionResult(schemaPath, netconfMessageRpcResult.getResult());
                }

                final String message = "Missing action result of action on schema path: " + schemaPath;
                LOG.error(message);
                throw new IllegalStateException(message);
            }, MoreExecutors.directExecutor());
        };
    }
}

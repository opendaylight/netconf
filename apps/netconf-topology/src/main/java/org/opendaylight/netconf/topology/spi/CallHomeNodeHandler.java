/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.CancellationException;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.network.topology.topology.topology.types.topology.netconf.SshTransportTopologyParameters;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeNodeHandler extends AbstractNodeHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeNodeHandler.class);

    private final CallHomeSessionInitializer sessionInitializer;

    private ListenableFuture<NetconfClientSession> sessionFuture;

    public CallHomeNodeHandler(final NetconfTimer timer,
            final BaseNetconfSchemaProvider baseSchemaProvider, final SchemaResourceManager schemaManager,
            final NetconfTopologySchemaAssembler schemaAssembler,
            final DeviceActionFactory deviceActionFactory, final RemoteDeviceHandler delegate,
            final RemoteDeviceId deviceId, final NodeId nodeId, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional, final SshTransportTopologyParameters sshParams,
            final @Nullable NegotiatedSshAlg callHomeSshAlg, final CallHomeSessionInitializer sessionInitializer) {
        super(timer, baseSchemaProvider, schemaManager, schemaAssembler,
            deviceActionFactory, delegate, deviceId, nodeId, node, nodeOptional, sshParams, callHomeSshAlg);
        this.sessionInitializer = requireNonNull(sessionInitializer);
    }

    @Override
    public synchronized void connect() {
        // TODO: we should get negotiatedSshAlg

        if (sessionFuture != null) {
            throw new IllegalStateException("Call-home session for " + deviceId + " has already been initialized");
        }
        if (isClosed()) {
            return;
        }

        final var future = sessionInitializer.initialize(communicator);
        sessionFuture = future;
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(final NetconfClientSession result) {
                // Nothing to do: the communicator reports the device as connected
            }

            @Override
            public void onFailure(final Throwable cause) {
                if (!(cause instanceof CancellationException)) {
                    onDeviceFailed(cause);
                }
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    void reconnectOrFail() {
        //TODO: maybe better to override onDeviceDisconnected() and onDeviceFailed()??
        delegate.onDeviceFailed(new ConnectGivenUpException("Given up connecting callhome device " + deviceId));
    }

    @Override
    void closeSession() {
        if (sessionFuture != null) {
            sessionFuture.cancel(false);
        }
    }
}

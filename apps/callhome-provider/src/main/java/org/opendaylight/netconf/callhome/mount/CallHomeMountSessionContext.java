/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import io.netty.util.concurrent.Future;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

// Non-final to allow mocking
class CallHomeMountSessionContext {
    @FunctionalInterface
    interface CloseCallback {
        void onClosed(CallHomeMountSessionContext deviceContext);
    }

    private final NodeId nodeId;
    private final CallHomeChannelActivator activator;
    private final CallHomeProtocolSessionContext protocol;
    private final CloseCallback onClose;
    // FIXME: Remove this
    private final ContextKey key;

    CallHomeMountSessionContext(final String nodeId, final CallHomeProtocolSessionContext protocol,
                                final CallHomeChannelActivator activator, final CloseCallback callback) {

        this.nodeId = new NodeId(requireNonNull(nodeId, "nodeId"));
        key = ContextKey.from(protocol.getRemoteAddress());
        this.protocol = requireNonNull(protocol, "protocol");
        this.activator = requireNonNull(activator, "activator");
        onClose = requireNonNull(callback, "callback");
    }

    CallHomeProtocolSessionContext getProtocol() {
        return protocol;
    }

    NodeId getId() {
        return nodeId;
    }

    ContextKey getContextKey() {
        return key;
    }

    /**
     * Create device default configuration.
     *
     * <p>
     * This configuration is a replacement of configuration device data
     * which is normally stored in configuration datastore but is absent for call-home devices.
     *
     * @return {@link Node} containing the default device configuration
     */
    // FIXME make these defaults tune-able in odl-netconf-callhome-server
    Node getConfigNode() {
        return new NodeBuilder()
                .setNodeId(getId())
                .addAugmentation(new NetconfNodeBuilder()
                        .setHost(new Host(key.getIpAddress()))
                        .setPort(key.getPort())
                        .setTcpOnly(false)
                        .setProtocol(new ProtocolBuilder()
                                .setName(protocol.getTransportType())
                                .build())
                        .setSchemaless(false)
                        .setReconnectOnChangedSchema(false)
                        .setConnectionTimeoutMillis(Uint32.valueOf(20000))
                        .setDefaultRequestTimeoutMillis(Uint32.valueOf(60000))
                        .setMaxConnectionAttempts(Uint32.ZERO)
                        .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(2000))
                        .setSleepFactor(Decimal64.valueOf("1.5"))
                        .setKeepaliveDelay(Uint32.valueOf(120))
                        .setConcurrentRpcLimit(Uint16.ZERO)
                        .setActorResponseWaitTime(Uint16.valueOf(5))
                        // the real call-home device credentials are applied in CallHomeAuthProviderImpl
                        .setCredentials(new LoginPasswordBuilder()
                                .setUsername("omitted")
                                .setPassword("omitted")
                                .build())
                        .setLockDatastore(true)
                    .build())
                .build();
    }

    Future<NetconfClientSession> activateNetconfChannel(final NetconfClientSessionListener sessionListener) {
        return activator.activate(wrap(sessionListener));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("address", protocol.getRemoteAddress())
                .add("hostKey", protocol.getRemoteServerKey())
                .toString();
    }

    private NetconfClientSessionListener wrap(final NetconfClientSessionListener delegate) {
        return new NetconfClientSessionListener() {
            @Override
            public void onSessionUp(final NetconfClientSession session) {
                delegate.onSessionUp(session);
            }

            @Override
            public void onSessionTerminated(final NetconfClientSession session, final NetconfTerminationReason reason) {
                try {
                    delegate.onSessionTerminated(session, reason);
                } finally {
                    removeSelf();
                }
            }

            @Override
            public void onSessionDown(final NetconfClientSession session, final Exception exc) {
                try {
                    removeSelf();
                } finally {
                    delegate.onSessionDown(session, exc);
                }
            }

            @Override
            public void onMessage(final NetconfClientSession session, final NetconfMessage message) {
                delegate.onMessage(session, message);
            }

            @Override
            public void onError(final NetconfClientSession session, final Exception failure) {
                delegate.onError(session, failure);
            }
        };
    }

    private void removeSelf() {
        onClose.onClosed(this);
    }
}

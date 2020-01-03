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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

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
        this.key = ContextKey.from(protocol.getRemoteAddress());
        this.protocol = requireNonNull(protocol, "protocol");
        this.activator = requireNonNull(activator, "activator");
        this.onClose = requireNonNull(callback, "callback");
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

    Node getConfigNode() {
        return new NodeBuilder()
                .setNodeId(getId())
                .addAugmentation(new NetconfNodeBuilder()
                    .setHost(new Host(key.getIpAddress()))
                    .setPort(key.getPort())
                    .setTcpOnly(Boolean.FALSE)
                    .setCredentials(new LoginPasswordBuilder()
                        .setUsername("ommited")
                        .setPassword("ommited")
                        .build())
                    .setSchemaless(Boolean.FALSE)
                    .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    <V> Promise<V> activateNetconfChannel(final NetconfClientSessionListener sessionListener) {
        return (Promise<V>) activator.activate(wrap(sessionListener));
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
            public void processMalformedRpc(final String messageId, final NetconfDocumentedException cause) {
                delegate.processMalformedRpc(messageId, cause);
            }
        };
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void removeSelf() {
        onClose.onClosed(this);
    }
}

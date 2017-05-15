/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import com.google.common.base.Preconditions;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

class CallHomeMountSessionContext {

    public interface CloseCallback {
        void onClosed(CallHomeMountSessionContext deviceContext);
    }

    private final NodeId nodeId;
    private final CallHomeChannelActivator activator;
    private final CallHomeProtocolSessionContext protocol;
    private final CloseCallback onClose;
    // FIXME: Remove this
    private final ContextKey key;

    CallHomeMountSessionContext(String nodeId, CallHomeProtocolSessionContext protocol,
                                CallHomeChannelActivator activator, CloseCallback callback) {

        this.nodeId = new NodeId(Preconditions.checkNotNull(nodeId, "nodeId"));
        this.key = ContextKey.from(protocol.getRemoteAddress());
        this.protocol = Preconditions.checkNotNull(protocol, "protocol");
        this.activator = Preconditions.checkNotNull(activator, "activator");
        this.onClose = Preconditions.checkNotNull(callback, "callback");
    }

    NodeId getId() {
        return nodeId;
    }

    public ContextKey getContextKey() {
        return key;
    }

    Node getConfigNode() {
        NodeBuilder builder = new NodeBuilder();
        return builder.setNodeId(getId()).addAugmentation(NetconfNode.class, configNetconfNode()).build();
    }

    private NetconfNode configNetconfNode() {
        NetconfNodeBuilder node = new NetconfNodeBuilder();
        node.setHost(new Host(key.getIpAddress()));
        node.setPort(key.getPort());
        node.setTcpOnly(Boolean.FALSE);
        node.setCredentials(new LoginPasswordBuilder().setUsername("ommited").setPassword("ommited").build());
        node.setSchemaless(Boolean.FALSE);
        return node.build();
    }

    @SuppressWarnings("unchecked")
    <V> Promise<V> activateNetconfChannel(NetconfClientSessionListener sessionListener) {
        return (Promise<V>) activator.activate(wrap(sessionListener));
    }

    @SuppressWarnings("deprecation")
    private NetconfClientSessionListener wrap(final NetconfClientSessionListener delegate) {
        return new NetconfClientSessionListener() {
            @Override
            public void onSessionUp(NetconfClientSession session) {
                delegate.onSessionUp(session);
            }

            @Override
            public void onSessionTerminated(NetconfClientSession session, NetconfTerminationReason reason) {
                try {
                    delegate.onSessionTerminated(session, reason);
                } finally {
                    removeSelf();
                }
            }

            @Override
            public void onSessionDown(NetconfClientSession session, Exception exc) {
                try {
                    removeSelf();
                } finally {
                    delegate.onSessionDown(session, exc);
                }
            }

            @Override
            public void onMessage(NetconfClientSession session, NetconfMessage message) {
                delegate.onMessage(session, message);
            }
        };
    }

    private void removeSelf() {
        onClose.onClosed(this);
    }

    InetSocketAddress getRemoteAddress() {
        return protocol.getRemoteAddress();
    }

    PublicKey getRemoteServerKey() {
        return protocol.getRemoteServerKey();
    }
}

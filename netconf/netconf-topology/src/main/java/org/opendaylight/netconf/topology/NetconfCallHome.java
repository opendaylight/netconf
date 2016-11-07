/*
 * Copyright (c) 2016 Cisco Systems, Inc, Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.opendaylight.netconf.topology;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.handler.stream.StreamIoHandler;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.common.SshdSocketAddress;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.conf.NetconfReversedClientConfiguration;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.HostBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by adetalhouet on 2016-11-07.
 */
public final class NetconfCallHome extends StreamIoHandler implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfCallHome.class);

    private static final String DEFAULT_BINDING_ADDRESS = "0.0.0.0";
    private final NioSocketAcceptor acceptor;
    private final Map<Long, AutoCloseable> openedSessions = Maps.newHashMap();
    private final AbstractNetconfTopology netconfTopology;

    private NetconfCallHome(final AbstractNetconfTopology netconfTopology) {
        acceptor = new NioSocketAcceptor();
        this.netconfTopology = netconfTopology;
    }

    @Override
    protected void processStreamIo(final IoSession ioSession, final InputStream in, final OutputStream out) {


        final NodeId nodeId = new NodeId("call-home");
        final Host host = HostBuilder.getDefaultInstance(DEFAULT_BINDING_ADDRESS);
        final PortNumber portNumber = new PortNumber(8833);
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(host)
                .setPort(portNumber)
                .build();

        AbstractNetconfTopology.NetconfConnectorDTO netconfConnectorDTO = netconfTopology.createDeviceCommunicator(nodeId, netconfNode);

        getClientConfig(netconfConnectorDTO.getCommunicator(), ioSession);

        synchronized (openedSessions) {
            // TODO check
            openedSessions.put(ioSession.getId(), new AutoCloseable() {
                @Override
                public void close() throws Exception {
                    netconfConnectorDTO.getCommunicator().close();
                    netconfConnectorDTO.getFacade().close();
                }
            });
        }
    }


    private NetconfReversedClientConfiguration getClientConfig(final NetconfDeviceCommunicator listener, IoSession tcpSession) {
        return new NetconfReversedClientConfiguration(1000L, new NetconfHelloMessageAdditionalHeader("a", DEFAULT_BINDING_ADDRESS,
                "8833", "ssh", "abc"), listener, new AuthenticationHandler() {
            @Override
            public String getUsername() {
                return "q";
            }

            @Override
            public AuthFuture authenticate(final ClientSession session) throws IOException {
                session.addPasswordIdentity("abcd");
                return session.auth();
            }
        }, tcpSession);
    }

    public static NetconfCallHome init(final AbstractNetconfTopology netconfTopology, final int port) {
        final NetconfCallHome handler = new NetconfCallHome(netconfTopology);
        handler.acceptor.setHandler(handler);
        try {
            handler.acceptor.bind(new SshdSocketAddress(DEFAULT_BINDING_ADDRESS, port));
        } catch (final IOException e) {
            // FIXME
            e.printStackTrace();
        }
        return handler;
    }

    @Override
    public void close() throws Exception {
        synchronized (openedSessions) {
            for (final AutoCloseable openedSession : openedSessions.values()) {
                openedSession.close();
            }
        }
        acceptor.unbind();
    }

    @Override
    public void sessionClosed(final IoSession ioSession) throws Exception {
        LOG.debug("Session from {} with id: {} is closing", ioSession.getRemoteAddress(), ioSession.getId());
        synchronized (openedSessions) {
            // TODO check
            openedSessions.get(ioSession.getId()).close();
        }
    }
}
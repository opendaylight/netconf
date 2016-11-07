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
import java.net.InetSocketAddress;
import java.util.Map;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.handler.stream.StreamIoHandler;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.conf.NetconfCallHomeClientConfiguration;
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

    public static final String DEFAULT_BINDING_ADDRESS = "0.0.0.0";

    private final NioSocketAcceptor acceptor;
    private final Map<String, AutoCloseable> openedSessions = Maps.newHashMap();
    private final AbstractNetconfTopology netconfTopology;

    public NetconfCallHome(final AbstractNetconfTopology netconfTopology) {
        acceptor = new NioSocketAcceptor();
        this.netconfTopology = netconfTopology;
    }

    public void init() {
        LOG.info("Netconf Call Home init");
        acceptor.setHandler(this);
        try {
            acceptor.bind(new InetSocketAddress(DEFAULT_BINDING_ADDRESS, 9119));
        } catch (final IOException e) {
            // FIXME
            e.printStackTrace();
        }
    }

    @Override
    protected void processStreamIo(final IoSession ioSession, final InputStream in, final OutputStream out) {
        if (ioSession.getRemoteAddress() instanceof InetSocketAddress) {

            final InetSocketAddress inetSocketAddress = (InetSocketAddress) ioSession.getRemoteAddress();

            LOG.info("Netconf Call Home processStreamIo {}", ioSession.getRemoteAddress());
            final NodeId nodeId = new NodeId("call-home-" + inetSocketAddress.getAddress().getHostAddress());

/*            boolean b = true;
            synchronized (openedSessions) {
                if (openedSessions.containsKey(inetSocketAddress.getAddress().getHostAddress())) {
                    LOG.info("{} session already exist", ioSession.getRemoteAddress());
                    ioSession.close(false);
                    b = false;
                }
            }*/

            final Host host = HostBuilder.getDefaultInstance(inetSocketAddress.getAddress().getHostAddress());
            final PortNumber portNumber = new PortNumber(830);
            final NetconfNode netconfNode = new NetconfNodeBuilder()
                    .setHost(host)
                    .setPort(portNumber)
                    .setSchemaless(false)
                    .build();

            final AbstractNetconfTopology.NetconfConnectorDTO netconfConnectorDTO = netconfTopology.createDeviceCommunicator(nodeId, netconfNode);
            final NetconfDeviceCommunicator deviceCommunicator = netconfConnectorDTO.getCommunicator();
            final NetconfCallHomeClientConfiguration clientConfig = getClientConfig(netconfConnectorDTO.getCommunicator(), ioSession,
                    InetSocketAddress.createUnresolved(inetSocketAddress.getAddress().getHostAddress(), 830));

            deviceCommunicator.initializeRemoteConnection(netconfTopology.clientDispatcher, clientConfig);

            synchronized (openedSessions) {
                // TODO check
                openedSessions.put(inetSocketAddress.getAddress().getHostAddress(), new AutoCloseable() {
                    @Override
                    public void close() throws Exception {
                        netconfConnectorDTO.getCommunicator().close();
                        netconfConnectorDTO.getFacade().close();
                    }
                });
            }
        } else {
            LOG.warn("The remoteAddress is of type {}", ioSession.getRemoteAddress().getClass());
        }
    }


    private NetconfCallHomeClientConfiguration getClientConfig(final NetconfDeviceCommunicator listener,
                                                               final IoSession tcpSession,
                                                               final InetSocketAddress inetSocketAddress) {
        NetconfHelloMessageAdditionalHeader helloHeader = new NetconfHelloMessageAdditionalHeader("vagrant", inetSocketAddress.getHostName(),
                String.valueOf(inetSocketAddress.getPort()), "ssh", "call-home");
        return new NetconfCallHomeClientConfiguration(1000L, inetSocketAddress, null, listener, new AuthenticationHandler() {
            @Override
            public String getUsername() {
                return "vagrant";
            }

            @Override
            public AuthFuture authenticate(final ClientSession session) throws IOException {
                LOG.info("Authenticating session {}", session);
                session.addPasswordIdentity("vagrant");
                return session.auth();
            }
        }, tcpSession);
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
        LOG.info("Session from {} with id: {} is closing", ioSession.getRemoteAddress(), ioSession.getId());
        final InetSocketAddress inetSocketAddress = (InetSocketAddress) ioSession.getRemoteAddress();

        synchronized (openedSessions) {
            // TODO check
            openedSessions.get(inetSocketAddress.getAddress().getHostAddress()).close();
            openedSessions.remove(inetSocketAddress.getAddress().getHostAddress());
        }
        super.sessionClosed(ioSession);
    }
}
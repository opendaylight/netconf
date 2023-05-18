/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.function.Consumer;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.netconf.transport.tls.TLSServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev221212.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev221212.TlsServerGrouping;

public interface NetconfServerFactory {

    ListenableFuture<TCPServer> createTcpServer(TcpServerGrouping params) throws UnsupportedConfigurationException;

    ListenableFuture<TLSServer> createTlsServer(TcpServerGrouping tcpParams, TlsServerGrouping tlsParams)
        throws UnsupportedConfigurationException;

    ListenableFuture<SSHServer> createSshServer(TcpServerGrouping tcpParams, SshServerGrouping sshParams)
        throws UnsupportedConfigurationException;

    ListenableFuture<SSHServer> createSshServer(TcpServerGrouping tcpParams, SshServerGrouping sshParams,
        Consumer<ServerFactoryManager> extFactoryConfiguration) throws UnsupportedConfigurationException;

}

/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.ssh;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.future.ConnectFuture;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.util.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSHServerTest {
    private static final String USER = "netconf";
    private static final String PASSWORD = "netconf";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 1830;
    private static final Logger LOG = LoggerFactory.getLogger(SSHServerTest.class);

    private File sshKeyPair;
    private SshProxyServer server;

    private final ExecutorService nioExec = Executors.newFixedThreadPool(1);
    private final EventLoopGroup clientGroup = new NioEventLoopGroup();
    private final ScheduledExecutorService minaTimerEx = Executors.newScheduledThreadPool(1);

    @Before
    public void setUp() throws Exception {
        sshKeyPair = Files.createTempFile("sshKeyPair", ".pem").toFile();
        sshKeyPair.deleteOnExit();

        LOG.info("Creating SSH server");

        final InetSocketAddress addr = InetSocketAddress.createUnresolved(HOST, PORT);
        server = new SshProxyServer(minaTimerEx, clientGroup, nioExec);
        server.bind(new SshProxyServerConfigurationBuilder()
                .setBindingAddress(addr).setLocalAddress(new LocalAddress("netconf"))
                .setAuthenticator((username, password) -> true)
                .setKeyPairProvider(SecurityUtils.createGeneratorHostKeyProvider(sshKeyPair.toPath()))
                .setIdleTimeout(Integer.MAX_VALUE).createSshProxyServerConfiguration());
        LOG.info("SSH server started on {}", PORT);
    }

    @Test
    public void connect() throws Exception {
        final SshClient sshClient = SshClient.setUpDefaultClient();
        sshClient.start();
        try {
            final ConnectFuture connect = sshClient.connect(USER, HOST, PORT);
            connect.await(30, TimeUnit.SECONDS);
            org.junit.Assert.assertTrue(connect.isConnected());
            final ClientSession session = connect.getSession();
            session.addPasswordIdentity(PASSWORD);
            final AuthFuture auth = session.auth();
            auth.await(30, TimeUnit.SECONDS);
            org.junit.Assert.assertTrue(auth.isSuccess());
        } finally {
            sshClient.close(true);
            server.close();
            clientGroup.shutdownGracefully().await();
            minaTimerEx.shutdownNow();
            nioExec.shutdownNow();
        }
    }
}

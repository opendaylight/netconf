/*
 * Copyright (c) 2017 Inocybe Technologies Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.ssh;

import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.sshd.common.util.ThreadUtils;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.util.osgi.NetconfConfigUtil;
import org.opendaylight.netconf.util.osgi.NetconfConfiguration;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfSSHProvider {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSSHProvider.class);

    private static final java.lang.String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 4096;
    public static final int POOL_SIZE = 8;
    private static final int DEFAULT_IDLE_TIMEOUT = Integer.MAX_VALUE;

    private final AuthProvider authProvider;

    private ScheduledExecutorService minaTimerExecutor;
    private NioEventLoopGroup clientGroup;
    private ExecutorService nioExecutor;

    private SshProxyServer server;

    public NetconfSSHProvider(final AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public void init() {
        minaTimerExecutor = Executors.newScheduledThreadPool(POOL_SIZE,
            runnable -> new Thread(runnable, "netconf-ssh-server-mina-timers"));
        clientGroup = new NioEventLoopGroup();
        nioExecutor = ThreadUtils.newFixedThreadPool("netconf-ssh-server-nio-group", POOL_SIZE);
        final BundleContext bundleContext = FrameworkUtil.getBundle(NetconfSSHProvider.class).getBundleContext();
        try {
            server = startSSHServer(bundleContext);
        } catch (final IOException | InvalidSyntaxException e) {
            LOG.error("Unable to start the SSH server", e);
        }
    }

    public void destroy() throws IOException {
        if (server != null) {
            server.close();
        }

        if (nioExecutor != null) {
            nioExecutor.shutdownNow();
        }

        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
        }

        if (minaTimerExecutor != null) {
            minaTimerExecutor.shutdownNow();
        }
    }

    private SshProxyServer startSSHServer(final BundleContext bundleContext)
            throws IOException, InvalidSyntaxException {
        final NetconfConfiguration netconfConfiguration =
                NetconfConfigUtil.getNetconfConfigurationService(bundleContext);

        final InetSocketAddress sshSocketAddress = netconfConfiguration.getSshServerAddress();
        LOG.info("Starting netconf SSH server at {}", sshSocketAddress);

        final LocalAddress localAddress = NetconfConfiguration.NETCONF_LOCAL_ADDRESS;

        final String path = netconfConfiguration.getPrivateKeyPath();
        LOG.trace("Starting netconf SSH server with path to ssh private key {}", path);

        final SshProxyServer sshProxyServer = new SshProxyServer(minaTimerExecutor, clientGroup, nioExecutor);
        sshProxyServer.bind(
                new SshProxyServerConfigurationBuilder()
                        .setBindingAddress(sshSocketAddress)
                        .setLocalAddress(localAddress)
                        .setAuthenticator(this.authProvider)
                        .setKeyPairProvider(new PEMGeneratorHostKeyProvider(path, ALGORITHM, KEY_SIZE))
                        .setIdleTimeout(DEFAULT_IDLE_TIMEOUT)
                        .createSshProxyServerConfiguration());
        return sshProxyServer;
    }
}

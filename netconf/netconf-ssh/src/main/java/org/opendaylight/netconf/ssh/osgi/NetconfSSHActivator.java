/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.ssh.osgi;

import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.sshd.common.util.ThreadUtils;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.opendaylight.netconf.ssh.SshProxyServer;
import org.opendaylight.netconf.ssh.SshProxyServerConfigurationBuilder;
import org.opendaylight.netconf.util.osgi.NetconfConfigUtil;
import org.opendaylight.netconf.util.osgi.NetconfConfiguration;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfSSHActivator {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSSHActivator.class);

    private static final java.lang.String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 4096;
    public static final int POOL_SIZE = 8;
    private static final int DEFAULT_IDLE_TIMEOUT = Integer.MAX_VALUE;

    private final BundleContext bundleContext;

    private ScheduledExecutorService minaTimerExecutor;
    private NioEventLoopGroup clientGroup;
    private ExecutorService nioExecutor;
    private AuthProviderTracker authProviderTracker;

    private SshProxyServer server;

    public NetconfSSHActivator(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    /**
     * Invoke by blueprint
     */
    public void start() {
        minaTimerExecutor = Executors.newScheduledThreadPool(POOL_SIZE, r -> new Thread(r, "netconf-ssh-server-mina-timers"));
        clientGroup = new NioEventLoopGroup();
        nioExecutor = ThreadUtils.newFixedThreadPool("netconf-ssh-server-nio-group", POOL_SIZE);
        server = startSSHServer(bundleContext);
    }

    private SshProxyServer startSSHServer(final BundleContext bundleContext) {
        final NetconfConfiguration netconfConfiguration = NetconfConfigUtil.getNetconfConfigurationService(bundleContext).
                orElseThrow(() -> new IllegalStateException("Configuration for SSH not found."));

        final InetSocketAddress sshSocketAddress = netconfConfiguration.getSshServerAddress();
        LOG.info("Starting netconf SSH server at {}", sshSocketAddress);

        final LocalAddress localAddress = NetconfConfigUtil.getNetconfLocalAddress();
        authProviderTracker = new AuthProviderTracker(bundleContext);

        final String path = netconfConfiguration.getPrivateKeyPath();
        LOG.trace("Starting netconf SSH server with path to ssh private key {}", path);

        final SshProxyServer sshProxyServer = new SshProxyServer(minaTimerExecutor, clientGroup, nioExecutor);

        try {
            sshProxyServer.bind(
                    new SshProxyServerConfigurationBuilder()
                            .setBindingAddress(sshSocketAddress)
                            .setLocalAddress(localAddress)
                            .setAuthenticator(authProviderTracker)
                            .setKeyPairProvider(new PEMGeneratorHostKeyProvider(path, ALGORITHM, KEY_SIZE))
                            .setIdleTimeout(DEFAULT_IDLE_TIMEOUT)
                            .createSshProxyServerConfiguration());
        } catch (IOException e) {
            LOG.error("Failed to bind ssh proxy server", e);
        }
        return sshProxyServer;
    }

    /**
     * Invoke by blueprint
     */
    public void stop() {
        if (server != null) {
            server.close();
        }

        if(authProviderTracker != null) {
            authProviderTracker.stop();
        }

        if(nioExecutor!=null) {
            nioExecutor.shutdownNow();
        }

        if(clientGroup != null) {
            clientGroup.shutdownGracefully();
        }

        if(minaTimerExecutor != null) {
            minaTimerExecutor.shutdownNow();
        }
    }
}

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
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.opendaylight.netconf.ssh.SshProxyServer;
import org.opendaylight.netconf.ssh.SshProxyServerConfigurationBuilder;
import org.opendaylight.netconf.util.osgi.NetconfConfigUtil;
import org.opendaylight.netconf.util.osgi.NetconfConfiguration;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfSSHActivator implements BundleActivator {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSSHActivator.class);

    private static final java.lang.String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 4096;
    public static final int POOL_SIZE = 8;
    private static final int DEFAULT_IDLE_TIMEOUT = Integer.MAX_VALUE;

    private ScheduledExecutorService minaTimerExecutor;
    private NioEventLoopGroup clientGroup;
    private ExecutorService nioExecutor;
    private AuthProviderTracker authProviderTracker;

    private SshProxyServer server;

    @Override
    public void start(final BundleContext bundleContext) throws IOException, InvalidSyntaxException {
        minaTimerExecutor = Executors.newScheduledThreadPool(POOL_SIZE,
            runnable -> new Thread(runnable, "netconf-ssh-server-mina-timers"));
        clientGroup = new NioEventLoopGroup();
        nioExecutor = ThreadUtils.newFixedThreadPool("netconf-ssh-server-nio-group", POOL_SIZE);
        server = startSSHServer(bundleContext);
    }

    @Override
    public void stop(final BundleContext context) throws IOException {
        if (server != null) {
            server.close();
        }

        if (authProviderTracker != null) {
            authProviderTracker.stop();
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
        authProviderTracker = new AuthProviderTracker(bundleContext);

        final String path = netconfConfiguration.getPrivateKeyPath();
        LOG.trace("Starting netconf SSH server with path to ssh private key {}", path);

        final SshProxyServer sshProxyServer = new SshProxyServer(minaTimerExecutor, clientGroup, nioExecutor);
        final AbstractGeneratorHostKeyProvider keyPairProvider = SecurityUtils.createGeneratorHostKeyProvider(null);
        keyPairProvider.setAlgorithm(ALGORITHM);
        keyPairProvider.setKeySize(KEY_SIZE);
        keyPairProvider.setFile(new File(path));
        sshProxyServer.bind(
                new SshProxyServerConfigurationBuilder()
                        .setBindingAddress(sshSocketAddress)
                        .setLocalAddress(localAddress)
                        .setAuthenticator(authProviderTracker)
                        .setKeyPairProvider(keyPairProvider)
                        .setIdleTimeout(DEFAULT_IDLE_TIMEOUT)
                        .createSshProxyServerConfiguration());
        return sshProxyServer;
    }
}

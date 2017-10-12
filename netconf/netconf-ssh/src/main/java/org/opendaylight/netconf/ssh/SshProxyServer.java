/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.ssh;

import com.google.common.collect.ImmutableList;
import io.netty.channel.EventLoopGroup;
import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.RuntimeSshException;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoConnector;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoServiceFactory;
import org.apache.sshd.common.io.IoServiceFactoryFactory;
import org.apache.sshd.common.io.nio2.Nio2Acceptor;
import org.apache.sshd.common.io.nio2.Nio2Connector;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.common.util.closeable.AbstractCloseable;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.SshServer;

/**
 * Proxy SSH server that just delegates decrypted content to a delegate server within same VM.
 * Implemented using Apache Mina SSH lib.
 */
public class SshProxyServer implements AutoCloseable {
    private final SshServer sshServer;
    private final ScheduledExecutorService minaTimerExecutor;
    private final EventLoopGroup clientGroup;
    private final IoServiceFactoryFactory nioServiceWithPoolFactoryFactory;

    public SshProxyServer(final ScheduledExecutorService minaTimerExecutor,
                          final EventLoopGroup clientGroup, final ExecutorService nioExecutor) {
        this.minaTimerExecutor = minaTimerExecutor;
        this.clientGroup = clientGroup;
        this.nioServiceWithPoolFactoryFactory =
                new NioServiceWithPoolFactory.NioServiceWithPoolFactoryFactory(nioExecutor);
        this.sshServer = SshServer.setUpDefaultServer();
    }

    public void bind(final SshProxyServerConfiguration sshProxyServerConfiguration) throws IOException {
        sshServer.setHost(sshProxyServerConfiguration.getBindingAddress().getHostString());
        sshServer.setPort(sshProxyServerConfiguration.getBindingAddress().getPort());

        //remove rc4 ciphers
        final List<NamedFactory<Cipher>> cipherFactories = sshServer.getCipherFactories();
        cipherFactories.removeIf(factory -> factory.getName().contains(BuiltinCiphers.arcfour128.getName())
                || factory.getName().contains(BuiltinCiphers.arcfour256.getName()));
        sshServer.setPasswordAuthenticator(
            (username, password, session)
                -> sshProxyServerConfiguration.getAuthenticator().authenticated(username, password));

        sshProxyServerConfiguration.getPublickeyAuthenticator().ifPresent(sshServer::setPublickeyAuthenticator);

        sshServer.setKeyPairProvider(sshProxyServerConfiguration.getKeyPairProvider());

        sshServer.setIoServiceFactoryFactory(nioServiceWithPoolFactoryFactory);
        sshServer.setScheduledExecutorService(minaTimerExecutor);
        sshServer.getProperties().put(ServerFactoryManager.IDLE_TIMEOUT,
            String.valueOf(sshProxyServerConfiguration.getIdleTimeout()));
        sshServer.getProperties().put(ServerFactoryManager.AUTH_TIMEOUT,
            String.valueOf(sshProxyServerConfiguration.getIdleTimeout()));

        final RemoteNetconfCommand.NetconfCommandFactory netconfCommandFactory =
                new RemoteNetconfCommand.NetconfCommandFactory(clientGroup,
                        sshProxyServerConfiguration.getLocalAddress());
        sshServer.setSubsystemFactories(ImmutableList.of(netconfCommandFactory));
        sshServer.start();
    }

    private static Map<String, String> getProperties(final SshProxyServerConfiguration sshProxyServerConfiguration) {
        final Map<String, String> ret = new HashMap<>();
        ret.put(ServerFactoryManager.IDLE_TIMEOUT, String.valueOf(sshProxyServerConfiguration.getIdleTimeout()));
        // TODO make auth timeout configurable on its own
        ret.put(ServerFactoryManager.AUTH_TIMEOUT, String.valueOf(sshProxyServerConfiguration.getIdleTimeout()));

        return ret;
    }

    @Override
    public void close() throws IOException {
        try {
            sshServer.stop(true);
        } finally {
            sshServer.close(true);
        }
    }

    /**
     * Based on Nio2ServiceFactory with one addition: injectable executor.
     */
    private static final class NioServiceWithPoolFactory extends AbstractCloseable implements IoServiceFactory {

        private final FactoryManager manager;
        private final AsynchronousChannelGroup group;

        NioServiceWithPoolFactory(final FactoryManager manager, final ExecutorService executor) {
            this.manager = manager;
            try {
                group = AsynchronousChannelGroup.withThreadPool(executor);
            } catch (final IOException e) {
                throw new RuntimeSshException(e);
            }
        }

        @Override
        public IoConnector createConnector(final IoHandler handler) {
            return new Nio2Connector(manager, handler, group);
        }

        @Override
        public IoAcceptor createAcceptor(final IoHandler handler) {
            return new Nio2Acceptor(manager, handler, group);
        }

        @SuppressWarnings("checkstyle:IllegalCatch")
        @Override
        protected void doCloseImmediately() {
            try {
                group.shutdownNow();
                group.awaitTermination(5, TimeUnit.SECONDS);
            } catch (final Exception e) {
                log.debug("Exception caught while closing channel group", e);
            } finally {
                super.doCloseImmediately();
            }
        }

        private static final class NioServiceWithPoolFactoryFactory extends Nio2ServiceFactoryFactory {

            private final ExecutorService nioExecutor;

            private NioServiceWithPoolFactoryFactory(final ExecutorService nioExecutor) {
                this.nioExecutor = nioExecutor;
            }

            @Override
            public IoServiceFactory create(final FactoryManager manager) {
                return new NioServiceWithPoolFactory(manager, nioExecutor);
            }
        }
    }

}

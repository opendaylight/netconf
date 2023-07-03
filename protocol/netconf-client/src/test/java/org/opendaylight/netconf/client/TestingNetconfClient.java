/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.Future;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;

/**
 * Synchronous netconf client suitable for testing.
 */
public class TestingNetconfClient implements Closeable {

    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;

    private final String label;
    private final NetconfClientSession clientSession;
    private final NetconfClientSessionListener sessionListener;

    public TestingNetconfClient(final String clientLabel,
                                final NetconfClientDispatcher netconfClientDispatcher,
                                final NetconfClientConfiguration config) throws InterruptedException {
        label = clientLabel;
        sessionListener = config.getSessionListener();
        Future<NetconfClientSession> clientFuture = netconfClientDispatcher.createClient(config);
        clientSession = get(clientFuture);
    }

    private static NetconfClientSession get(final Future<NetconfClientSession> clientFuture)
            throws InterruptedException {
        try {
            return clientFuture.get();
        } catch (CancellationException e) {
            throw new RuntimeException("Cancelling " + TestingNetconfClient.class.getSimpleName(), e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to create " + TestingNetconfClient.class.getSimpleName(), e);
        }
    }

    public Future<NetconfMessage> sendRequest(final NetconfMessage message) {
        return ((SimpleNetconfClientSessionListener) sessionListener).sendRequest(message);
    }

    public NetconfMessage sendMessage(final NetconfMessage message, final int attemptMsDelay) throws ExecutionException,
            InterruptedException, TimeoutException {
        return sendRequest(message).get(attemptMsDelay, TimeUnit.MILLISECONDS);
    }

    public NetconfMessage sendMessage(final NetconfMessage message) throws ExecutionException,
            InterruptedException, TimeoutException {
        return sendMessage(message, DEFAULT_CONNECT_TIMEOUT);
    }

    @Override
    public void close() throws IOException {
        clientSession.close();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TestingNetconfClient{");
        sb.append("label=").append(label);
        sb.append(", sessionId=").append(sessionId().getValue());
        sb.append('}');
        return sb.toString();
    }

    public @NonNull SessionIdType sessionId() {
        return clientSession.sessionId();
    }

    public Set<String> getCapabilities() {
        checkState(clientSession != null, "Client was not initialized successfully");
        return Sets.newHashSet(clientSession.getServerCapabilities());
    }

    public static void main(final String[] args) throws Exception {
        HashedWheelTimer hashedWheelTimer = new HashedWheelTimer();
        NioEventLoopGroup nettyGroup = new NioEventLoopGroup();
        NetconfClientDispatcherImpl netconfClientDispatcher = new NetconfClientDispatcherImpl(nettyGroup, nettyGroup,
                hashedWheelTimer);
        LoginPasswordHandler authHandler = new LoginPasswordHandler("admin", "admin");
        TestingNetconfClient client = new TestingNetconfClient("client", netconfClientDispatcher,
                getClientConfig("127.0.0.1", 1830, true, Optional.of(authHandler)));
        System.console().writer().println(client.getCapabilities());
    }

    private static NetconfClientConfiguration getClientConfig(final String host, final int port, final boolean ssh,
            final Optional<? extends AuthenticationHandler> maybeAuthHandler) throws UnknownHostException {
        InetSocketAddress netconfAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        final NetconfClientConfigurationBuilder b = NetconfClientConfigurationBuilder.create()
            .withAddress(netconfAddress)
            .withSessionListener(new SimpleNetconfClientSessionListener());
        if (ssh) {
            b.withProtocol(NetconfClientProtocol.SSH).withAuthHandler(maybeAuthHandler.orElseThrow());
        } else {
            b.withProtocol(NetconfClientProtocol.TCP);
        }
        return b.build();
    }
}

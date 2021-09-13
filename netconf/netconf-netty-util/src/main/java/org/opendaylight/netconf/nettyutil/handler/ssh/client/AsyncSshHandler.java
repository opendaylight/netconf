/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.shaded.sshd.client.channel.ClientChannel;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.future.ConnectFuture;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.core.CoreModuleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty SSH handler class. Acts as interface between Netty and SSH library.
 */
public class AsyncSshHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncSshHandler.class);

    public static final String SUBSYSTEM = "netconf";

    public static final int SSH_DEFAULT_NIO_WORKERS = 8;

    public static final NetconfSshClient DEFAULT_CLIENT;

    static {
        final NetconfSshClient c = new NetconfClientBuilder().build();
        // Disable default timeouts from mina sshd
        c.getProperties().put(CoreModuleProperties.AUTH_TIMEOUT.getName(), "0");
        c.getProperties().put(CoreModuleProperties.IDLE_TIMEOUT.getName(), "0");
        c.getProperties().put(CoreModuleProperties.NIO2_READ_TIMEOUT.getName(), "0");
        c.getProperties().put(CoreModuleProperties.TCP_NODELAY.getName(), true);

        // TODO make configurable, or somehow reuse netty threadpool
        c.setNioWorkers(SSH_DEFAULT_NIO_WORKERS);
        c.start();
        DEFAULT_CLIENT = c;
    }

    private final AtomicBoolean isDisconnected = new AtomicBoolean();
    private final AuthenticationHandler authenticationHandler;
    private final Future<?> negotiationFuture;
    private final NetconfSshClient sshClient;
    private final String nodeId;

    private AsyncSshHandlerWriter sshWriteAsyncHandler;

    private NettyAwareChannelSubsystem channel;
    private ClientSession session;
    private ChannelPromise connectPromise;
    private FutureListener<Object> negotiationFutureListener;
    private SocketAddress remoteAddress;

    public AsyncSshHandler(final AuthenticationHandler authenticationHandler, final NetconfSshClient sshClient,
                           final Future<?> negotiationFuture, final String nodeId) {
        this.authenticationHandler = requireNonNull(authenticationHandler);
        this.sshClient = requireNonNull(sshClient);
        this.negotiationFuture = negotiationFuture;
        this.nodeId = nodeId;
    }

    /**
     * Constructor of {@code AsyncSshHandler}.
     *
     * @param authenticationHandler authentication handler
     * @param sshClient             started SshClient
     * @param nodeId                node identifier
     */
    public AsyncSshHandler(final AuthenticationHandler authenticationHandler, final NetconfSshClient sshClient,
                           final String nodeId) {
        this(authenticationHandler, sshClient, null, nodeId);
    }

    public static AsyncSshHandler createForNetconfSubsystem(final AuthenticationHandler authenticationHandler,
                                                            final String nodeId) {
        return new AsyncSshHandler(authenticationHandler, DEFAULT_CLIENT, nodeId);
    }

    /**
     * Create AsyncSshHandler for netconf subsystem. Negotiation future has to be set to success after successful
     * netconf negotiation.
     *
     * @param authenticationHandler authentication handler
     * @param negotiationFuture     negotiation future
     * @param nodeId                node identifier
     * @return                      {@code AsyncSshHandler}
     */
    public static AsyncSshHandler createForNetconfSubsystem(final AuthenticationHandler authenticationHandler,
            final Future<?> negotiationFuture, final @Nullable NetconfSshClient sshClient,
            final String nodeId) {
        return new AsyncSshHandler(authenticationHandler, sshClient != null ? sshClient : DEFAULT_CLIENT,
                negotiationFuture, nodeId);
    }

    private void startSsh(final ChannelHandlerContext ctx, final SocketAddress address) throws IOException {
        LOG.debug("{}: Starting SSH to {} on channel: {}", nodeId, address, ctx.channel());
        remoteAddress = address;

        final ConnectFuture sshConnectionFuture = sshClient.connect(authenticationHandler.getUsername(), address)
               .verify(ctx.channel().config().getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
        sshConnectionFuture.addListener(future -> {
            if (future.isConnected()) {
                handleSshSessionCreated(future, ctx);
            } else {
                handleSshSetupFailure(ctx, future.getException());
            }
        });
    }

    private synchronized void handleSshSessionCreated(final ConnectFuture future, final ChannelHandlerContext ctx) {
        try {
            LOG.trace("{}: SSH session created on channel: {}", nodeId, ctx.channel());

            session = future.getSession();
            verify(session instanceof NettyAwareClientSession, "Unexpected session %s", session);

            final AuthFuture authenticateFuture = authenticationHandler.authenticate(session);
            final NettyAwareClientSession localSession = (NettyAwareClientSession) session;
            authenticateFuture.addListener(future1 -> {
                if (future1.isSuccess()) {
                    handleSshAuthenticated(localSession, ctx);
                } else {
                    handleSshSetupFailure(ctx, new AuthenticationFailedException(
                        nodeId + ": Authentication failed", future1.getException()));
                }
            });
        } catch (final IOException e) {
            handleSshSetupFailure(ctx, e);
        }
    }

    private synchronized void handleSshAuthenticated(final NettyAwareClientSession newSession,
            final ChannelHandlerContext ctx) {
        LOG.debug("{}: SSH session authenticated on channel: {}, server version: {}",
                nodeId, ctx.channel(), newSession.getServerVersion());

        try {
            channel = newSession.createSubsystemChannel(SUBSYSTEM, ctx);
            channel.setStreaming(ClientChannel.Streaming.Async);
            channel.open().addListener(future -> {
                if (future.isOpened()) {
                    handleSshChanelOpened(ctx);
                } else {
                    handleSshSetupFailure(ctx, future.getException());
                }
            });
        } catch (final IOException e) {
            handleSshSetupFailure(ctx, e);
        }
    }

    private synchronized void handleSshChanelOpened(final ChannelHandlerContext ctx) {
        LOG.trace("{}: SSH subsystem channel opened successfully on channel: {}", nodeId, ctx.channel());

        if (negotiationFuture == null) {
            connectPromise.setSuccess();
        }

        sshWriteAsyncHandler = new AsyncSshHandlerWriter(channel.getAsyncIn());
        ctx.fireChannelActive();
        channel.onClose(() -> disconnect(ctx, ctx.newPromise()));
    }

    private synchronized void handleSshSetupFailure(final ChannelHandlerContext ctx, final Throwable error) {
        LOG.warn("{}: Unable to setup SSH connection on channel: {}. Session to device {} was terminated",
                nodeId, ctx.channel(), remoteAddress, error);

        // If the promise is not yet done, we have failed with initial connect and set connectPromise to failure
        if (!connectPromise.isDone()) {
            connectPromise.setFailure(error);
        }
        disconnect(ctx, ctx.newPromise());
    }

    @Override
    public synchronized void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        sshWriteAsyncHandler.write(ctx, msg, promise);
    }

    @Override
    public synchronized void connect(final ChannelHandlerContext ctx, final SocketAddress remoteSocketAddress,
                                     final SocketAddress localAddress, final ChannelPromise promise) throws Exception {
        LOG.debug("{}: SSH session connecting on channel {}. promise: {} ", nodeId, ctx.channel(), promise);
        connectPromise = requireNonNull(promise);

        if (negotiationFuture != null) {
            negotiationFutureListener = future -> {
                if (future.isSuccess()) {
                    promise.setSuccess();
                }
            };
            //complete connection promise with netconf negotiation future
            negotiationFuture.addListener(negotiationFutureListener);
        }
        startSsh(ctx, remoteSocketAddress);
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (isDisconnected.compareAndSet(false, true)) {
            logAndClose(ctx, promise);
        }
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (isDisconnected.compareAndSet(false, true)) {
            logAndDisconnect(ctx, promise);
        }
    }

    private synchronized void logAndClose(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (connectPromise.isSuccess()) {
            LOG.warn("{}: SSH session on channel {} to device {} was closed by client",
                    nodeId, ctx.channel(), remoteAddress);
        }
        safelyDisconnect(ctx, promise);
    }

    private synchronized void logAndDisconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (connectPromise.isSuccess()) {
            LOG.warn("{}: SSH session on channel {} to device {} was terminated by remote host",
                    nodeId, ctx.channel(), remoteAddress);
        }
        safelyDisconnect(ctx, promise);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void safelyDisconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        LOG.trace("{}: Closing SSH session on channel: {} with connect promise in state: {}",
                nodeId, ctx.channel(), connectPromise);

        // If we have already succeeded and the session was dropped after,
        // we need to fire inactive to notify reconnect logic
        if (connectPromise.isSuccess()) {
            ctx.fireChannelInactive();
        }

        if (sshWriteAsyncHandler != null) {
            sshWriteAsyncHandler.close();
        }

        //If connection promise is not already set, it means negotiation failed
        //we must set connection promise to failure
        if (!connectPromise.isDone()) {
            connectPromise.setFailure(new IllegalStateException(nodeId + ": Negotiation failed"));
        }

        //Remove listener from negotiation future, we don't want notifications
        //from negotiation anymore
        if (negotiationFuture != null) {
            negotiationFuture.removeListener(negotiationFutureListener);
        }

        if (session != null && !session.isClosed() && !session.isClosing()) {
            session.close(false).addListener(future -> {
                synchronized (this) {
                    if (!future.isClosed()) {
                        session.close(true);
                    }
                    session = null;
                }
            });
        }

        // Super disconnect is necessary in this case since we are using NioSocketChannel and it needs
        // to cleanup its resources e.g. Socket that it tries to open in its constructor
        // (https://bugs.opendaylight.org/show_bug.cgi?id=2430)
        // TODO better solution would be to implement custom ChannelFactory + Channel
        // that will use mina SSH lib internally: port this to custom channel implementation
        try {
            // Disconnect has to be closed after inactive channel event was fired, because it interferes with it
            super.disconnect(ctx, ctx.newPromise());
        } catch (final Exception e) {
            LOG.warn("{}: Unable to cleanup all resources for channel: {}. Ignoring.", nodeId, ctx.channel(), e);
        }

        if (channel != null) {
            //TODO: see if calling just close() is sufficient
            //channel.close(false);
            channel.close();
            channel = null;
        }
        promise.setSuccess();
        LOG.debug("{}: SSH session closed on channel: {}", nodeId, ctx.channel());
    }
}

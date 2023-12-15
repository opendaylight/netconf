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
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.shaded.sshd.client.channel.ClientChannel;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.future.ConnectFuture;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.PropertyResolverUtils;
import org.opendaylight.netconf.shaded.sshd.core.CoreModuleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty SSH handler class. Acts as interface between Netty and SSH library.
 */
public final class AsyncSshHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncSshHandler.class);
    private static final VarHandle DISCONNECTED;

    static {
        try {
            DISCONNECTED = MethodHandles.lookup().findVarHandle(AsyncSshHandler.class, "disconnected", boolean.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static final String SUBSYSTEM = "netconf";

    public static final int SSH_DEFAULT_NIO_WORKERS = 8;

    public static final NetconfSshClient DEFAULT_CLIENT;

    static {
        final var c = new NetconfClientBuilder().build();
        // Disable default timeouts from mina sshd
        final var zero = Duration.ofMillis(0);
        CoreModuleProperties.AUTH_TIMEOUT.set(c, zero);
        CoreModuleProperties.IDLE_TIMEOUT.set(c, zero);
        CoreModuleProperties.NIO2_READ_TIMEOUT.set(c, zero);
        CoreModuleProperties.TCP_NODELAY.set(c, true);

        // TODO make configurable, or somehow reuse netty threadpool
        c.setNioWorkers(SSH_DEFAULT_NIO_WORKERS);
        c.start();
        DEFAULT_CLIENT = c;
    }

    private final AuthenticationHandler authenticationHandler;
    private final Future<?> negotiationFuture;
    private final NetconfSshClient sshClient;
    private final String name;
    private final Timer timer;

    // Initialized by connect()
    @GuardedBy("this")
    private ChannelPromise connectPromise;

    private AsyncSshHandlerWriter sshWriteAsyncHandler;
    private NettyAwareChannelSubsystem channel;
    private ClientSession session;
    private FutureListener<Object> negotiationFutureListener;

    private volatile boolean disconnected;

    private AsyncSshHandler(final AuthenticationHandler authenticationHandler, final NetconfSshClient sshClient,
            final Timer timer, final @Nullable Future<?> negotiationFuture, final @Nullable String name) {
        this.authenticationHandler = requireNonNull(authenticationHandler);
        this.sshClient = requireNonNull(sshClient);
        this.timer = requireNonNull(timer);
        this.negotiationFuture = negotiationFuture;
        this.name = name != null && !name.isBlank() ? name : "UNNAMED";
    }

    public AsyncSshHandler(final AuthenticationHandler authenticationHandler, final NetconfSshClient sshClient,
            final Timer timer, final @Nullable Future<?> negotiationFuture) {
        this(authenticationHandler, sshClient, timer, negotiationFuture, null);
    }

    /**
     * Constructor of {@code AsyncSshHandler}.
     *
     * @param authenticationHandler authentication handler
     * @param sshClient             started SshClient
     */
    public AsyncSshHandler(final AuthenticationHandler authenticationHandler, final NetconfSshClient sshClient,
            final Timer timer) {
        this(authenticationHandler, sshClient, timer, null);
    }

    public static AsyncSshHandler createForNetconfSubsystem(final AuthenticationHandler authenticationHandler,
            final Timer timer) {
        return new AsyncSshHandler(authenticationHandler, DEFAULT_CLIENT, timer);
    }

    /**
     * Create AsyncSshHandler for netconf subsystem. Negotiation future has to be set to success after successful
     * netconf negotiation.
     *
     * @param authenticationHandler authentication handler
     * @param negotiationFuture     negotiation future
     * @return                      {@code AsyncSshHandler}
     */
    public static AsyncSshHandler createForNetconfSubsystem(final AuthenticationHandler authenticationHandler,
            final Future<?> negotiationFuture, final Timer timer, final @Nullable NetconfSshClient sshClient,
            final @Nullable String name) {
        return new AsyncSshHandler(authenticationHandler, sshClient != null ? sshClient : DEFAULT_CLIENT, timer,
                negotiationFuture, name);
    }

    @Override
    public synchronized void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        sshWriteAsyncHandler.write(ctx, msg, promise);
    }

    @Override
    public synchronized void connect(final ChannelHandlerContext ctx, final SocketAddress remoteAddress,
            final SocketAddress localAddress, final ChannelPromise promise) throws IOException {
        LOG.debug("{}: SSH session connecting on channel {}. promise: {}", name, ctx.channel(), promise);
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
        // Synchronize SshClient timeout with timeout configured in device ConnectTimeoutMillis configuration
        final var connectTimeoutMillis = ctx.channel().config().getConnectTimeoutMillis();
        PropertyResolverUtils.updateProperty(sshClient.getProperties(),
            CoreModuleProperties.IO_CONNECT_TIMEOUT.getName(), Duration.ofMillis(connectTimeoutMillis).getSeconds());

        LOG.debug("{}: Starting SSH to {} on channel: {}", name, remoteAddress, ctx.channel());
        final var sshConnectFuture = sshClient.connect(authenticationHandler.getUsername(), remoteAddress);
        final var timeout = setTimeoutToConnectFuture(connectTimeoutMillis, sshConnectFuture);
        sshConnectFuture.addListener(future -> {
            if (timeout != null && !timeout.isExpired()) {
                timeout.cancel();
            }
            onConnectComplete(future, ctx);
        });
    }

    private Timeout setTimeoutToConnectFuture(final int connectTimeoutMillis, final ConnectFuture sshConnectFuture) {
        return timer.newTimeout(unused -> {
            if (!sshConnectFuture.isDone()) {
                // Connection timed out
                sshConnectFuture.setException(new TimeoutException("Connection timed out for session: "
                    + sshConnectFuture.getId()));
                sshConnectFuture.cancel();
            }
        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized void onConnectComplete(final ConnectFuture connectFuture, final ChannelHandlerContext ctx) {
        final var cause = connectFuture.getException();
        if (cause != null) {
            onOpenFailure(ctx, cause);
            return;
        }

        final var clientSession = connectFuture.getSession();
        LOG.trace("{}: SSH session {} created on channel: {}", name, clientSession, ctx.channel());
        verify(clientSession instanceof NettyAwareClientSession, "Unexpected session %s", clientSession);

        final var localSession = (NettyAwareClientSession) clientSession;
        session = localSession;

        final AuthFuture authFuture;
        try {
            authFuture = authenticationHandler.authenticate(localSession);
        } catch (final IOException e) {
            onOpenFailure(ctx, e);
            return;
        }

        authFuture.addListener(future -> onAuthComplete(future, localSession, ctx));
    }

    private synchronized void onAuthComplete(final AuthFuture authFuture, final NettyAwareClientSession clientSession,
            final ChannelHandlerContext ctx) {
        final var cause = authFuture.getException();
        if (cause != null) {
            onOpenFailure(ctx, new AuthenticationFailedException("Authentication failed", cause));
            return;
        }
        if (disconnected) {
            LOG.debug("{}: Skipping SSH subsystem allocation, channel: {}", name, ctx.channel());
            return;
        }

        LOG.debug("{}: SSH session authenticated on channel: {}, server version: {}", name, ctx.channel(),
            clientSession.getServerVersion());

        final OpenFuture openFuture;
        try {
            channel = clientSession.createSubsystemChannel(SUBSYSTEM, ctx);
            channel.setStreaming(ClientChannel.Streaming.Async);
            openFuture = channel.open();
        } catch (final IOException e) {
            onOpenFailure(ctx, e);
            return;
        }

        openFuture.addListener(future -> ctx.executor().execute(() -> onOpenComplete(future, ctx)));
    }

    // This callback has to run on the channel's executor because it runs fireChannelActive(), which needs to be
    // delivered synchronously. If we were to execute on some other thread we would end up delaying the event,
    // potentially creating havoc in the pipeline.
    private synchronized void onOpenComplete(final OpenFuture openFuture, final ChannelHandlerContext ctx) {
        final var cause = openFuture.getException();
        if (cause != null) {
            onOpenFailure(ctx, cause);
            return;
        }
        if (disconnected) {
            LOG.trace("{}: Skipping activation, channel: {}", name, ctx.channel());
            return;
        }

        LOG.trace("{}: SSH subsystem channel opened successfully on channel: {}", name, ctx.channel());
        if (negotiationFuture == null) {
            connectPromise.setSuccess();
        }

        sshWriteAsyncHandler = new AsyncSshHandlerWriter(channel.getAsyncIn());
        ctx.fireChannelActive();
        channel.onClose(() -> disconnect(ctx, ctx.newPromise()));
    }

    @Holding("this")
    private void onOpenFailure(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.warn("{}: Unable to setup SSH connection on channel: {}", name, ctx.channel(), cause);

        // If the promise is not yet done, we have failed with initial connect and set connectPromise to failure
        if (!connectPromise.isDone()) {
            connectPromise.setFailure(cause);
        }

        disconnect(ctx, ctx.newPromise());
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        disconnect(ctx, promise);
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (DISCONNECTED.compareAndSet(this, false, true)) {
            ctx.executor().execute(() -> safelyDisconnect(ctx, promise));
        }
    }

    // This method has the potential to interact with the channel pipeline, for example via fireChannelInactive(). These
    // callbacks need to complete during execution of this method and therefore this method needs to be executing on
    // the channel's executor.
    @SuppressWarnings("checkstyle:IllegalCatch")
    private synchronized void safelyDisconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        LOG.trace("{}: Closing SSH session on channel: {} with connect promise in state: {}", name, ctx.channel(),
            connectPromise);

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
            connectPromise.setFailure(new IllegalStateException("Negotiation failed"));
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
            LOG.warn("{}: Unable to cleanup all resources for channel: {}. Ignoring.", name, ctx.channel(), e);
        }

        if (channel != null) {
            //TODO: see if calling just close() is sufficient
            //channel.close(false);
            channel.close();
            channel = null;
        }
        promise.setSuccess();
        LOG.debug("{}: SSH session closed on channel: {}", name, ctx.channel());
    }
}

/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.channel.Channel;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;

final class SshClientChannelInitializer extends AbstractClientChannelInitializer {
    private final AuthenticationHandler authenticationHandler;
    private final Timer timer;
    private final @Nullable NetconfSshClient sshClient;
    private final @Nullable String name;

    SshClientChannelInitializer(final AuthenticationHandler authHandler,
            final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final NetconfClientSessionListener sessionListener, final Timer timer,
            final @Nullable NetconfSshClient sshClient, final @Nullable String name) {
        super(negotiatorFactory, sessionListener);
        authenticationHandler = authHandler;
        this.timer = timer;
        this.sshClient = sshClient;
        this.name = name;
    }

    SshClientChannelInitializer(final AuthenticationHandler authHandler,
            final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final NetconfClientSessionListener sessionListener, final Timer timer) {
        this(authHandler, negotiatorFactory, sessionListener, timer, null, null);
    }

    @Override
    public void initialize(final Channel ch, final Promise<NetconfClientSession> promise) {
        // ssh handler has to be the first handler in pipeline
        var asyncHandler = AsyncSshHandler.createForNetconfSubsystem(authenticationHandler, promise, timer, sshClient,
            name);
        ch.pipeline().addFirst(asyncHandler);
        super.initialize(ch, promise);
    }
}

/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfClientBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;

public class NetconfCallHomeServerBuilder {
    private static final long DEFAULT_SESSION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);
    private static final int DEFAULT_CALL_HOME_PORT = 4334;

    private NetconfSshClient sshClient;
    private EventLoopGroup nettyGroup;
    private NetconfClientSessionNegotiatorFactory negotiationFactory;
    private InetSocketAddress bindAddress;

    private final CallHomeAuthorizationProvider authProvider;
    private final CallHomeNetconfSubsystemListener subsystemListener;
    private final StatusRecorder recorder;

    public NetconfCallHomeServerBuilder(final CallHomeAuthorizationProvider authProvider,
            final CallHomeNetconfSubsystemListener subsystemListener, final StatusRecorder recorder) {
        this.authProvider = authProvider;
        this.subsystemListener = subsystemListener;
        this.recorder = recorder;
    }

    public @NonNull NetconfCallHomeServer build() {
        return new NetconfCallHomeServer(sshClient(), authProvider(),
            new CallHomeSessionContext.Factory(nettyGroup(), negotiatorFactory(), subsystemListener()),
            bindAddress(), recorder);
    }

    public NetconfSshClient getSshClient() {
        return sshClient;
    }

    public void setSshClient(final NetconfSshClient sshClient) {
        this.sshClient = sshClient;
    }

    public EventLoopGroup getNettyGroup() {
        return nettyGroup;
    }

    public void setNettyGroup(final EventLoopGroup nettyGroup) {
        this.nettyGroup = nettyGroup;
    }

    public NetconfClientSessionNegotiatorFactory getNegotiationFactory() {
        return negotiationFactory;
    }

    public void setNegotiationFactory(final NetconfClientSessionNegotiatorFactory negotiationFactory) {
        this.negotiationFactory = negotiationFactory;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(final InetSocketAddress bindAddress) {
        this.bindAddress = bindAddress;
    }

    public CallHomeAuthorizationProvider getAuthProvider() {
        return authProvider;
    }

    private InetSocketAddress bindAddress() {
        return bindAddress != null ? bindAddress : defaultBindAddress();
    }

    private EventLoopGroup nettyGroup() {
        return nettyGroup != null ? nettyGroup : defaultNettyGroup();
    }

    private NetconfClientSessionNegotiatorFactory negotiatorFactory() {
        return negotiationFactory != null ? negotiationFactory : defaultNegotiationFactory();
    }

    private CallHomeNetconfSubsystemListener subsystemListener() {
        return subsystemListener;
    }

    private CallHomeAuthorizationProvider authProvider() {
        return authProvider;
    }

    private NetconfSshClient sshClient() {
        return sshClient != null ? sshClient : defaultSshClient();
    }

    private static NetconfSshClient defaultSshClient() {
        return new NetconfClientBuilder().build();
    }

    private static NetconfClientSessionNegotiatorFactory defaultNegotiationFactory() {
        return new NetconfClientSessionNegotiatorFactory(new HashedWheelTimer(),
                                                         Optional.empty(), DEFAULT_SESSION_TIMEOUT_MILLIS);
    }

    private static EventLoopGroup defaultNettyGroup() {
        return new DefaultEventLoopGroup();
    }

    private static InetSocketAddress defaultBindAddress() {
        return new InetSocketAddress(DEFAULT_CALL_HOME_PORT);
    }
}

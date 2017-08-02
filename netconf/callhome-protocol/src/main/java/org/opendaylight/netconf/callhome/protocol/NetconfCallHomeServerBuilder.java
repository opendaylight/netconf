/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import com.google.common.base.Optional;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.SshClient;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.callhome.protocol.CallHomeSessionContext.Factory;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.yangtools.concepts.Builder;

public class NetconfCallHomeServerBuilder implements Builder<NetconfCallHomeServer> {

    private static final long DEFAULT_SESSION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);
    private static final int DEFAULT_CALL_HOME_PORT = 6666;

    private SshClient sshClient;
    private EventLoopGroup nettyGroup;
    private NetconfClientSessionNegotiatorFactory negotiationFactory;
    private InetSocketAddress bindAddress;

    private final CallHomeAuthorizationProvider authProvider;
    private final CallHomeNetconfSubsystemListener subsystemListener;
    private final StatusRecorder recorder;

    public NetconfCallHomeServerBuilder(CallHomeAuthorizationProvider authProvider,
                                        CallHomeNetconfSubsystemListener subsystemListener, StatusRecorder recorder) {
        this.authProvider = authProvider;
        this.subsystemListener = subsystemListener;
        this.recorder = recorder;
    }

    @Override
    public NetconfCallHomeServer build() {
        Factory factory =
                new CallHomeSessionContext.Factory(nettyGroup(), negotiatorFactory(), subsystemListener());
        return new NetconfCallHomeServer(sshClient(), authProvider(), factory, bindAddress(), this.recorder);
    }

    public SshClient getSshClient() {
        return sshClient;
    }

    public void setSshClient(SshClient sshClient) {
        this.sshClient = sshClient;
    }

    public EventLoopGroup getNettyGroup() {
        return nettyGroup;
    }

    public void setNettyGroup(EventLoopGroup nettyGroup) {
        this.nettyGroup = nettyGroup;
    }

    public NetconfClientSessionNegotiatorFactory getNegotiationFactory() {
        return negotiationFactory;
    }

    public void setNegotiationFactory(NetconfClientSessionNegotiatorFactory negotiationFactory) {
        this.negotiationFactory = negotiationFactory;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(InetSocketAddress bindAddress) {
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

    private SshClient sshClient() {
        return sshClient != null ? sshClient : defaultSshClient();
    }

    private SshClient defaultSshClient() {
        return SshClient.setUpDefaultClient();
    }

    private NetconfClientSessionNegotiatorFactory defaultNegotiationFactory() {
        return new NetconfClientSessionNegotiatorFactory(new HashedWheelTimer(),
                Optional.absent(), DEFAULT_SESSION_TIMEOUT_MILLIS);
    }

    private EventLoopGroup defaultNettyGroup() {
        return new LocalEventLoopGroup();
    }

    private InetSocketAddress defaultBindAddress() {
        return new InetSocketAddress(DEFAULT_CALL_HOME_PORT);
    }
}

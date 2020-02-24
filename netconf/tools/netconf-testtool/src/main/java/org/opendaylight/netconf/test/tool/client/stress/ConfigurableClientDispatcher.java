/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.client.stress;

import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import java.util.Optional;
import java.util.Set;
import org.apache.sshd.client.SshClient;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;

public final class ConfigurableClientDispatcher extends NetconfClientDispatcherImpl {

    private final Set<String> capabilities;

    private ConfigurableClientDispatcher(final EventLoopGroup bossGroup, final EventLoopGroup workerGroup,
            final Timer timer, final Set<String> capabilities, final Optional<SshClient> sshClient) {
        super(bossGroup, workerGroup, timer, sshClient);
        this.capabilities = capabilities;
    }

    /**
     * EXI + chunked framing.
     */
    public static ConfigurableClientDispatcher createChunkedExi(final EventLoopGroup bossGroup,
            final EventLoopGroup workerGroup, final Timer timer, final Optional<SshClient> sshClient) {
        return new ConfigurableClientDispatcher(bossGroup, workerGroup, timer,
            NetconfClientSessionNegotiatorFactory.EXI_CLIENT_CAPABILITIES, sshClient);
    }

    /**
     * EXI + ]]gt;]]gt; framing.
     */
    public static ConfigurableClientDispatcher createLegacyExi(final EventLoopGroup bossGroup,
            final EventLoopGroup workerGroup, final Timer timer, final Optional<SshClient> sshClient) {
        return new ConfigurableClientDispatcher(bossGroup, workerGroup, timer,
            NetconfClientSessionNegotiatorFactory.LEGACY_EXI_CLIENT_CAPABILITIES, sshClient);
    }

    /**
     * Chunked framing.
     */
    public static ConfigurableClientDispatcher createChunked(final EventLoopGroup bossGroup,
            final EventLoopGroup workerGroup, final Timer timer, final Optional<SshClient> sshClient) {
        return new ConfigurableClientDispatcher(bossGroup, workerGroup, timer,
            NetconfClientSessionNegotiatorFactory.DEFAULT_CLIENT_CAPABILITIES, sshClient);
    }

    /**
     * ]]gt;]]gt; framing.
     */
    public static ConfigurableClientDispatcher createLegacy(final EventLoopGroup bossGroup,
            final EventLoopGroup workerGroup, final Timer timer, final Optional<SshClient> sshClient) {
        return new ConfigurableClientDispatcher(bossGroup, workerGroup, timer,
            NetconfClientSessionNegotiatorFactory.LEGACY_FRAMING_CLIENT_CAPABILITIES, sshClient);
    }

    @Override
    protected NetconfClientSessionNegotiatorFactory getNegotiatorFactory(final NetconfClientConfiguration cfg) {
        return new NetconfClientSessionNegotiatorFactory(getTimer(), cfg.getAdditionalHeader(),
            cfg.getConnectionTimeoutMillis(), capabilities);
    }
}

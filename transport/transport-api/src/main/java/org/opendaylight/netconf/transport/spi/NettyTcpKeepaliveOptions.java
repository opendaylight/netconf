/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import io.netty.channel.ChannelOption;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Channel options for configuring TCP keepalive parameters.
 *
 * @param tcpKeepCnt the option corresponding to {@code TCP_KEEPCNT}
 * @param tcpKeepIdle the option corresponding to {@code TCP_KEEPIDLE}
 * @param tcpKeepIntvl the option corresponding to {@code TCP_KEEPINTVL}
 */
@Beta
@NonNullByDefault
public record NettyTcpKeepaliveOptions(
        ChannelOption<Integer> tcpKeepCnt,
        ChannelOption<Integer> tcpKeepIdle,
        ChannelOption<Integer> tcpKeepIntvl) {
    public NettyTcpKeepaliveOptions {
        requireNonNull(tcpKeepCnt);
        requireNonNull(tcpKeepIdle);
        requireNonNull(tcpKeepIntvl);
    }
}
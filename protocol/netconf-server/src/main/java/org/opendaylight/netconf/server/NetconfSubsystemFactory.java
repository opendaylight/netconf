/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelSession;
import org.opendaylight.netconf.shaded.sshd.server.command.Command;
import org.opendaylight.netconf.shaded.sshd.server.subsystem.SubsystemFactory;

public final class NetconfSubsystemFactory implements SubsystemFactory {
    private static final String NETCONF = "netconf";

    private final ServerChannelInitializer channelInitializer;

    public NetconfSubsystemFactory(final ServerChannelInitializer channelInitializer) {
        this.channelInitializer = requireNonNull(channelInitializer);
    }

    @Override
    public String getName() {
        return NETCONF;
    }

    @Override
    public Command createSubsystem(final ChannelSession channel) throws IOException {
        return new NetconfSubsystem(NETCONF, channelInitializer);
    }
}

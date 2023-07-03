/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;

/**
 * Extension interface allowing to customize {@link ChannelSubsystem} to be used with {@link SSHClient} instance.
 */
// FIXME: NETCONF-1108: do we really need this?
public final class ClientSubsystemFactory {
    private final ClientSubsystem.@NonNull Initializer initializer;
    private final @NonNull String subsystemName;

    public ClientSubsystemFactory(final String subsystemName, final ClientSubsystem.Initializer initializer) {
        this.subsystemName = requireNonNull(subsystemName);
        this.initializer = requireNonNull(initializer);
    }

    @NonNull String subsystemName() {
        return subsystemName;
    }

    @NonNull ClientSubsystem createSubsystemChannel() {
        return new ClientSubsystem(subsystemName, initializer);
    }
}

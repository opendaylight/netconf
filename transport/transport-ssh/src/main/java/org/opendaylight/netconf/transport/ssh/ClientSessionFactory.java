/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.channel.ChannelSubsystem;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;
import org.opendaylight.netconf.shaded.sshd.client.session.SessionFactory;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.session.ConnectionService;

final class ClientSessionFactory extends SessionFactory {

    private final String username;
    private final ClientSubsystemFactory subsystemFactory;

    ClientSessionFactory(final @NonNull ClientFactoryManager factoryManager, final  @NonNull String username,
            @Nullable final ClientSubsystemFactory subsystemFactory) {
        super(factoryManager);
        this.username = requireNonNull(username);
        this.subsystemFactory = subsystemFactory;
    }

    @Override
    protected ClientSessionImpl doCreateSession(final IoSession ioSession) throws Exception {
        return new ClientSessionImpl(getFactoryManager(), ioSession) {
            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public ChannelSubsystem createSubsystemChannel(final String subsystem) throws IOException {
                requireNonNull(subsystem);
                if (subsystemFactory != null && subsystem.equals(subsystemFactory.subsystemName())) {
                    final var channel = subsystemFactory.createSubsystemChannel();
                    final ConnectionService service = getConnectionService();
                    final long id = service.registerChannel(channel);
                    if (log.isDebugEnabled()) {
                        log.debug("createSubsystemChannel({})[{}] created id={}", this, channel.getSubsystem(), id);
                    }
                    return channel;
                }
                return super.createSubsystemChannel(subsystem);
            }
        };
    }
}

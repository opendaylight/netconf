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
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;

/**
 * Client session listener. Auto-opens requested subsystem channel immediately after successful authentication.
 */
public class ClientSubsystemSessionListener implements SessionListener {
    private final String subsystemName;

    public ClientSubsystemSessionListener(final String subsystemName) {
        this.subsystemName = requireNonNull(subsystemName);
    }

    @Override
    public void sessionEvent(final Session session, final Event event) {
        if (Event.Authenticated == event && session instanceof ClientSession clientSession) {
            try {
                final var subsystem = clientSession.createSubsystemChannel(subsystemName);
                subsystem.onClose(() -> session.close(true));
                subsystem.open().addListener(future -> {
                    final var failure = future.getException();
                    if (failure != null) {
                        session.exceptionCaught(failure);
                    }
                });
            } catch (IOException e) {
                session.exceptionCaught(e);
            }
        }
    }
}

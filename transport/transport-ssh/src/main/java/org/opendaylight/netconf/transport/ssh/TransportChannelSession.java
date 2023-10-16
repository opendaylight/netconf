/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.common.channel.RequestHandler;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelSession;

/**
 * Our own version of {@link ChannelSession}, bound to a backend Netty channel.
 */
final class TransportChannelSession extends ChannelSession {
    private final TransportServerSession serverSession;

    TransportChannelSession(final TransportServerSession serverSession) {
        this.serverSession = requireNonNull(serverSession);
    }

    @Override
    protected RequestHandler.Result handleSubsystemParsed(final String request, final String subsystem)
            throws IOException {
        final var openSubsystem = serverSession.openSubsystem(subsystem);
        if (openSubsystem == null) {
            log.warn("handleSubsystemParsed({}) Unsupported subsystem: {}", this, subsystem);
            return RequestHandler.Result.ReplyFailure;
        }

        commandInstance = openSubsystem;
        final var prepareResult = prepareChannelCommand(request, commandInstance);
        if (prepareResult == RequestHandler.Result.ReplySuccess) {
            openSubsystem.onPrepareComplete();
        }
        return prepareResult;
    }
}

/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.base.VerifyException;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import org.opendaylight.netconf.shaded.sshd.common.channel.ChannelAsyncOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.transport.api.TransportChannel;

/**
 * Common trait handling the attachment of a SSH subsystem (client or server) to an underlay {@link TransportChannel}.
 */
sealed interface TransportSubsystem permits TransportClientSubsystem, TransportServerSubsystem {
    /**
     * Invoked when the underlying channel becomes inactive.
     *
     * @throws IOException when an I/O error occurs
     */
    void substemChannelInactive() throws IOException;

    default ChannelHandlerContext attachToUnderlay(final IoOutputStream out, final TransportChannel underlay) {
        if (!(out instanceof ChannelAsyncOutputStream asyncOut)) {
            throw new VerifyException("Unexpected output " + out);
        }

        // Note that there may be multiple handlers already present on the channel, hence we are attaching last, but
        // from the logical perspective we are the head handlers.
        final var pipeline = underlay.channel().pipeline();

        // outbound packet handler, i.e. moving bytes from the channel into SSHD's pipeline
        pipeline.addLast(new OutboundChannelHandler(asyncOut));

        // invoke requested action on channel termination
        underlay.channel().closeFuture().addListener(future -> substemChannelInactive());

        // last handler context is used as entry point to direct inbound packets (captured by SSH adapter)
        // back to same channel pipeline
        return pipeline.lastContext();
    }
}

/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.opendaylight.netconf.shaded.sshd.common.io.IoInputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelDataReceiver;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelSession;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelSessionAware;
import org.opendaylight.netconf.shaded.sshd.server.command.AbstractCommandSupport;
import org.opendaylight.netconf.shaded.sshd.server.command.AsyncCommand;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TransportServerSubsystem extends AbstractCommandSupport
        implements AsyncCommand, ChannelSessionAware, ChannelDataReceiver, TransportSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(TransportServerSubsystem.class);

    private final TransportChannel underlay;

    private SettableFuture<ChannelHandlerContext> future;
    private ChannelHandlerContext head;

    TransportServerSubsystem(final String name, final TransportChannel underlay,
            final SettableFuture<ChannelHandlerContext> future) {
        super(name, null);
        this.underlay = requireNonNull(underlay);
        this.future = requireNonNull(future);
    }

    @Override
    public void run() {
        // not used
    }

    @Override
    public void setIoInputStream(final IoInputStream in) {
        // not used
    }

    @Override
    public void setIoErrorStream(final IoOutputStream err) {
        // not used
    }

    @Override
    public void setIoOutputStream(final IoOutputStream out) {
        head = attachToUnderlay(out, underlay);
    }

    @Override
    public void setChannelSession(final ChannelSession session) {
        // set ourselves as the handler for inbound packets
        session.setDataReceiver(this);
    }

    @Override
    public int data(final ChannelSession channel, final byte[] buf, final int start, final int len) {
        // Do not propagate empty invocations
        if (len > 0) {
            LOG.debug("Forwarding {} bytes of data on {}", len, channel);
            head.fireChannelRead(Unpooled.copiedBuffer(buf, start, len));
        }
        return len;
    }

    @Override
    public void substemChannelInactive() {
        onExit(0);
    }

    @Override
    public void close() {
        // No-op?
    }

    void onPrepareComplete() {
        future.set(verifyNotNull(head, "setIoOutputStream() should have been called"));
        future = null;
        // set additional info for upcoming netconf session:
        //     final var session = getServerSession();
        //     final var address = (InetSocketAddress) session.getClientAddress();
        //     final var additionalHeader =  new NetconfHelloMessageAdditionalHeader(session.getUsername(),
        //         address.getAddress().getHostAddress(), String.valueOf(address.getPort()), "ssh", "client")
        //         .toFormattedString();
        //     head.fireChannelRead(Unpooled.wrappedBuffer(additionalHeader.getBytes(StandardCharsets.UTF_8)));
    }
}
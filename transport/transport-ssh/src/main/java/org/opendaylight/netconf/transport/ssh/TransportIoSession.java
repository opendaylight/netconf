/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelInboundHandler;
import java.io.IOException;
import java.net.SocketAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.shaded.sshd.common.future.CloseFuture;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFutureListener;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.IoService;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.io.IoWriteFuture;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.Buffer;

@NonNullByDefault
final class TransportIoSession implements IoSession {
    private final IoHandler handler;
    private final SocketAddress acceptanceAddress;
    private final long id;

    TransportIoSession(final IoHandler handler, final SocketAddress acceptanceAddress, final long id) {
        this.handler = requireNonNull(handler);
        this.acceptanceAddress = requireNonNull(acceptanceAddress);
        this.id = id;
    }

    ChannelInboundHandler handler() {
        return new OdlFlowControlHandler(handler, this);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public SocketAddress getAcceptanceAddress() {
        return acceptanceAddress;
    }

    @Override
    public Object getAttribute(final Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object setAttribute(final Object key, final Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object setAttributeIfAbsent(final Object key, final Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object removeAttribute(final Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IoWriteFuture writeBuffer(final Buffer buffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public CloseFuture close(final boolean immediately) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addCloseFutureListener(final SshFutureListener<CloseFuture> listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeCloseFutureListener(final SshFutureListener<CloseFuture> listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosing() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IoService getService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdownOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void suspendRead() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resumeRead() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getLocalAddress() {
        throw new UnsupportedOperationException();
    }
}

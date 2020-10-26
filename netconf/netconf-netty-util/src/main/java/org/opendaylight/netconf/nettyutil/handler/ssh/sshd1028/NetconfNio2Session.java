/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.sshd1028;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.nio2.Nio2CompletionHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.nio2.Nio2Service;
import org.opendaylight.netconf.shaded.sshd.common.io.nio2.Nio2Session;
import org.opendaylight.netconf.shaded.sshd.common.util.Readable;

/**
 * Custom Nio2Session which fixes the issue with connections not being properly closed.
 * Should be removed when SSHD-1028 is fixed.
 */
public class NetconfNio2Session extends Nio2Session {

    public NetconfNio2Session(final Nio2Service service, final FactoryManager manager, final IoHandler handler,
                              final AsynchronousSocketChannel socket, final SocketAddress acceptanceAddress)
        throws IOException {
        super(service, manager, handler, socket, acceptanceAddress);
    }

    /**
     * This method in sshd-osgi:2.5.0 and 2.5.1 contains a bug. The close(true) statement was removed. We can override
     * it making a workaround for this issue - until SSHD-1028 is fixed.
     */
    @Override
    @SuppressWarnings("IllegalCatch")
    protected void handleReadCycleCompletion(final ByteBuffer buffer, final Readable bufReader,
                                             final Nio2CompletionHandler<Integer, Object> completionHandler,
                                             final Integer result, final Object attachment) {
        try {
            boolean debugEnabled = log.isDebugEnabled();
            if (result >= 0) {
                if (debugEnabled) {
                    log.debug("handleReadCycleCompletion({}) read {} bytes", this, result);
                }
                buffer.flip();
                IoHandler handler = getIoHandler();
                handler.messageReceived(this, bufReader);
                if (!closeFuture.isClosed()) {
                    // re-use reference for next iteration since we finished processing it
                    buffer.clear();
                    doReadCycle(buffer, completionHandler);
                } else {
                    if (debugEnabled) {
                        log.debug("handleReadCycleCompletion({}) IoSession has been closed, stop reading", this);
                    }
                }
            } else {
                if (debugEnabled) {
                    log.debug("handleReadCycleCompletion({}) Socket has been disconnected (result={}), closing "
                        + "IoSession now", this, result);
                }
                close(true);
            }
        } catch (Throwable exc) {
            completionHandler.failed(exc, attachment);
        }
    }
}

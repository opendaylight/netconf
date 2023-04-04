/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import java.util.ArrayDeque;
import java.util.Queue;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleNetconfClientSessionListener implements NetconfClientSessionListener {
    private static final class RequestEntry {
        private final Promise<NetconfMessage> promise;
        private final NetconfMessage request;

        RequestEntry(final Promise<NetconfMessage> future, final NetconfMessage request) {
            promise = requireNonNull(future);
            this.request = requireNonNull(request);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SimpleNetconfClientSessionListener.class);

    @GuardedBy("this")
    private final Queue<RequestEntry> requests = new ArrayDeque<>();

    @GuardedBy("this")
    private NetconfClientSession clientSession;

    @Holding("this")
    private void dispatchRequest() {
        while (!requests.isEmpty()) {
            final RequestEntry e = requests.peek();
            if (e.promise.setUncancellable()) {
                LOG.debug("Sending message {}", e.request);
                clientSession.sendMessage(e.request);
                break;
            }

            LOG.debug("Message {} has been cancelled, skipping it", e.request);
            requests.remove();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public final synchronized void onSessionUp(final NetconfClientSession clientSession) {
        this.clientSession = requireNonNull(clientSession);
        LOG.debug("Client session {} went up", clientSession);
        dispatchRequest();
    }

    private synchronized void tearDown(final Exception cause) {
        final RequestEntry e = requests.poll();
        if (e != null) {
            e.promise.setFailure(cause);
        }

        clientSession = null;
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public final void onSessionDown(final NetconfClientSession clientSession, final Exception exception) {
        LOG.debug("Client Session {} went down unexpectedly", clientSession, exception);
        tearDown(exception);
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public final void onSessionTerminated(final NetconfClientSession clientSession,
                                          final NetconfTerminationReason netconfTerminationReason) {
        LOG.debug("Client Session {} terminated, reason: {}", clientSession,
                netconfTerminationReason.getErrorMessage());
        tearDown(new RuntimeException(netconfTerminationReason.getErrorMessage()));
    }

    @Override
    public synchronized void onMessage(final NetconfClientSession session, final NetconfMessage message) {
        LOG.debug("New message arrived: {}", message);

        final RequestEntry e = requests.poll();
        if (e != null) {
            e.promise.setSuccess(message);
            dispatchRequest();
        } else {
            LOG.info("Ignoring unsolicited message {}", message);
        }
    }

    @Override
    public synchronized void onError(final NetconfClientSession session, final Exception failure) {
        LOG.debug("New error arrived: {}", failure.toString());

        final RequestEntry e = requests.poll();
        if (e != null) {
            e.promise.setFailure(failure);
            dispatchRequest();
        } else {
            LOG.info("Ignoring unsolicited error {}", failure.toString());
        }
    }

    public final synchronized Future<NetconfMessage> sendRequest(final NetconfMessage message) {
        final RequestEntry req = new RequestEntry(GlobalEventExecutor.INSTANCE.newPromise(), message);

        requests.add(req);
        if (clientSession != null) {
            dispatchRequest();
        }

        return req.promise;
    }
}

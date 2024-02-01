/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server;

import static java.util.Objects.requireNonNull;

import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.callhome.server.ssh.CallHomeSshAuthProvider;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCallHomeSessionContextManager<T extends CallHomeSessionContext>
        implements CallHomeSessionContextManager<T> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCallHomeSessionContextManager.class);

    private final ConcurrentMap<String, Registration> contexts = new ConcurrentHashMap<>();
    private final CallHomeStatusRecorder statusRecorder;
    private final CallHomeSshAuthProvider authProvider;

    protected AbstractCallHomeSessionContextManager(final CallHomeStatusRecorder statusRecorder,
            final CallHomeSshAuthProvider authProvider) {
        this.statusRecorder = requireNonNull(statusRecorder);
        this.authProvider = requireNonNull(authProvider);
    }

    @Override
    public final Registration createSession(final ClientSession clientSession, final SocketAddress remoteAddress,
            final PublicKey serverKey) {
        final var authSettings = authProvider.provideAuth(remoteAddress, serverKey);
        if (authSettings == null) {
            // no auth for server key
            statusRecorder.reportUnknown(remoteAddress, serverKey);
            LOG.info("No auth settings found. Connection from {} rejected.", remoteAddress);
            return null;
        }
        final var id = authSettings.id();
        final var context = createContext(id, clientSession);
        if (context == null) {
            // if there is an issue creating context then the cause expected to be
            // logged within overridden createContext() method
            return null;
        }

        final var reg = new AbstractRegistration() {
            @Override
            protected void removeRegistration() {
                contexts.remove(id, this);
                context.close();
                context.settableFuture().cancel(false);
            }
        };

        final var prev = contexts.putIfAbsent(id, reg);
        if (prev != null) {
            LOG.info("Session context with same id {} already exists. Connection from {} rejected.",
                authSettings.id(), remoteAddress);
            return null;
        }

        // Session context is ok, apply auth settings to current session
        authSettings.applyTo(clientSession);
        LOG.debug("Session context is created for SSH session: {}", context);
        return reg;
    }

    @Override
    public final void close() {
        for (var it = contexts.values().iterator(); it.hasNext(); ) {
            it.next().close();
            it.remove();
        }
    }

    protected abstract @Nullable T createContext(String id, ClientSession clientSession);
}

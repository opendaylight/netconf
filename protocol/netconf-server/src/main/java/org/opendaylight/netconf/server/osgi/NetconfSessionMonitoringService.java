/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.checkerframework.checker.lock.qual.Holding;
import org.opendaylight.netconf.server.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionEvent;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SessionsBuilder;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements {@link SessionListener} to receive updates about Netconf sessions. Instance notifies its
 * listeners about session start and end. It also publishes on regular interval list of sessions,
 * where events like rpc or notification happened.
 */
abstract sealed class NetconfSessionMonitoringService implements SessionListener, AutoCloseable {
    static final class WithoutUpdates extends NetconfSessionMonitoringService {
        WithoutUpdates() {
            LOG.info("/netconf-state/sessions will not be updated.");
        }

        @Override
        void startTask() {
           // No-op
        }

        @Override
        void stopTask() {
            // No-op
        }
    }

    static final class WithUpdates extends NetconfSessionMonitoringService {
        private final ScheduledExecutorService executor;
        private final long period;
        private final TimeUnit timeUnit;

        private ScheduledFuture<?> task;

        WithUpdates(final ScheduledExecutorService executor, final long period, final TimeUnit timeUnit) {
            this.executor = requireNonNull(executor);
            this.period = period;
            this.timeUnit = requireNonNull(timeUnit);
            LOG.info("/netconf-state/sessions will be updated every {} {}.", period, timeUnit);
        }

        @Override
        void startTask() {
            if (task == null) {
                task = executor.scheduleAtFixedRate(this::updateSessionStats, period, period, timeUnit);
            }
        }

        @Override
        void stopTask() {
            if (task != null) {
                task.cancel(false);
                task = null;
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfSessionMonitoringService.class);

    private final Set<NetconfManagementSession> sessions = new HashSet<>();
    private final Set<NetconfManagementSession> changedSessions = new HashSet<>();
    private final Set<NetconfMonitoringService.SessionsListener> listeners = new HashSet<>();

    synchronized Sessions getSessions() {
        return new SessionsBuilder()
            .setSession(BindingMap.of(
                sessions.stream().map(NetconfManagementSession::toManagementSession).collect(Collectors.toList())))
            .build();
    }

    @Override
    public final synchronized void onSessionUp(final NetconfManagementSession session) {
        LOG.debug("Session {} up", session);
        if (!sessions.add(session)) {
            throw new IllegalStateException("Session " + session + " was already added");
        }

        final var mgmt = session.toManagementSession();
        for (var listener : listeners) {
            listener.onSessionStarted(mgmt);
        }
    }

    @Override
    public final synchronized void onSessionDown(final NetconfManagementSession session) {
        LOG.debug("Session {} down", session);
        if (!sessions.remove(session)) {
            throw new IllegalStateException("Session " + session + " not present");
        }
        changedSessions.remove(session);

        final var mgmt = session.toManagementSession();
        for (var listener : listeners) {
            listener.onSessionEnded(mgmt);
        }
    }

    @Override
    public final synchronized void onSessionEvent(final SessionEvent event) {
        changedSessions.add(event.getSession());
    }

    final synchronized Registration registerListener(final NetconfMonitoringService.SessionsListener listener) {
        listeners.add(listener);
        startTask();
        return new AbstractRegistration() {
            @Override
            protected void removeRegistration() {
                listeners.remove(listener);
            }
        };
    }

    @Override
    public final synchronized void close() {
        stopTask();
        listeners.clear();
        sessions.clear();
    }

    @Holding("this")
    abstract void startTask();

    @Holding("this")
    abstract void stopTask();

    final synchronized void updateSessionStats() {
        if (!changedSessions.isEmpty()) {
            final var updatedSessions = changedSessions.stream()
                .map(NetconfManagementSession::toManagementSession)
                .collect(ImmutableList.toImmutableList());
            changedSessions.clear();

            for (var listener : listeners) {
                listener.onSessionsUpdated(updatedSessions);
            }
        }
    }
}

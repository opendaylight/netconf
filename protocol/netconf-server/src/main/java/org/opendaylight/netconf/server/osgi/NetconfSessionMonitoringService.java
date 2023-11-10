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
class NetconfSessionMonitoringService implements SessionListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSessionMonitoringService.class);

    private final Set<NetconfManagementSession> sessions = new HashSet<>();
    private final Set<NetconfManagementSession> changedSessions = new HashSet<>();
    private final Set<NetconfMonitoringService.SessionsListener> listeners = new HashSet<>();
    private final ScheduledExecutorService executor;
    private final long updateSeconds;

    private ScheduledFuture<?> task;

    NetconfSessionMonitoringService() {
        executor = null;
        updateSeconds = -1;
    }

    /**
     * Constructor for {@code NetconfSessionMonitoringService}.
     *
     * @param schedulingThreadPool thread pool for scheduling session stats updates
     * @param updateSeconds update interval. If is less than 0, updates won't be scheduled
     */
    NetconfSessionMonitoringService(final ScheduledExecutorService executor, final long updateSeconds) {
        this.updateSeconds = updateSeconds;
        if (updateSeconds > 0) {
            LOG.info("/netconf-state/sessions will be updated every {} seconds.", updateSeconds);
            this.executor = requireNonNull(executor);
        } else {
            LOG.info("update interval {}: /netconf-state/sessions won't be updated.", updateSeconds);
            this.executor = null;
        }
    }

    synchronized Sessions getSessions() {
        return new SessionsBuilder()
            .setSession(BindingMap.of(
                sessions.stream().map(NetconfManagementSession::toManagementSession).collect(Collectors.toList())))
            .build();
    }

    @Override
    public synchronized void onSessionUp(final NetconfManagementSession session) {
        LOG.debug("Session {} up", session);
        if (!sessions.add(session)) {
            throw new IllegalStateException("Session " + session + " was already added");
        }
        notifySessionUp(session);
    }

    @Override
    public synchronized void onSessionDown(final NetconfManagementSession session) {
        LOG.debug("Session {} down", session);
        if (!sessions.remove(session)) {
            throw new IllegalStateException("Session " + session + " not present");
        }
        changedSessions.remove(session);
        notifySessionDown(session);
    }

    @Override
    public synchronized void onSessionEvent(final SessionEvent event) {
        changedSessions.add(event.getSession());
    }

    synchronized Registration registerListener(final NetconfMonitoringService.SessionsListener listener) {
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
    public synchronized void close() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        listeners.clear();
        sessions.clear();
    }

    private synchronized void updateSessionStats() {
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

    private void notifySessionUp(final NetconfManagementSession managementSession) {
        final var session = managementSession.toManagementSession();
        for (var listener : listeners) {
            listener.onSessionStarted(session);
        }
    }

    private void notifySessionDown(final NetconfManagementSession managementSession) {
        final var session = managementSession.toManagementSession();
        for (var listener : listeners) {
            listener.onSessionEnded(session);
        }
    }

    private void startTask() {
        if (executor != null && task == null) {
            task = executor.scheduleAtFixedRate(this::updateSessionStats, updateSeconds, updateSeconds,
                TimeUnit.SECONDS);
        }
    }
}

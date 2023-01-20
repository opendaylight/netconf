/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.netconf.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.api.monitoring.SessionEvent;
import org.opendaylight.netconf.api.monitoring.SessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SessionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
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
    private final long updateIntervalSeconds;
    private boolean running;

    /**
     * Constructor for {@code NetconfSessionMonitoringService}.
     *
     * @param schedulingThreadPool  thread pool for scheduling session stats updates. If not present, updates won't be
     *                              scheduled.
     * @param updateIntervalSeconds update interval. If is less than 0, updates won't be scheduled
     */
    NetconfSessionMonitoringService(final Optional<ScheduledThreadPool> schedulingThreadPool,
            final long updateIntervalSeconds) {
        this.updateIntervalSeconds = updateIntervalSeconds;
        if (schedulingThreadPool.isPresent() && updateIntervalSeconds > 0) {
            executor = schedulingThreadPool.get().getExecutor();
            LOG.info("/netconf-state/sessions will be updated every {} seconds.", updateIntervalSeconds);
        } else {
            LOG.info(
                "Scheduling thread pool is present = {}, update interval {}: /netconf-state/sessions won't be updated.",
                schedulingThreadPool.isPresent(), updateIntervalSeconds);
            executor = null;
        }
    }

    synchronized Sessions getSessions() {
        return new SessionsBuilder()
                .setSession(Maps.uniqueIndex(
                    Collections2.transform(sessions, NetconfManagementSession::toManagementSession), Session::key))
                .build();
    }

    @Override
    public synchronized void onSessionUp(final NetconfManagementSession session) {
        LOG.debug("Session {} up", session);
        checkState(!sessions.contains(session), "Session %s was already added", session);
        sessions.add(session);
        notifySessionUp(session);
    }

    @Override
    public synchronized void onSessionDown(final NetconfManagementSession session) {
        LOG.debug("Session {} down", session);
        checkState(sessions.contains(session), "Session %s not present", session);
        sessions.remove(session);
        changedSessions.remove(session);
        notifySessionDown(session);
    }

    @Override
    public synchronized void onSessionEvent(final SessionEvent event) {
        changedSessions.add(event.getSession());
    }

    synchronized AutoCloseable registerListener(final NetconfMonitoringService.SessionsListener listener) {
        listeners.add(listener);
        if (!running) {
            startUpdateSessionStats();
        }
        return () -> listeners.remove(listener);
    }

    @Override
    public synchronized void close() {
        stopUpdateSessionStats();
        listeners.clear();
        sessions.clear();
    }

    private synchronized void updateSessionStats() {
        if (changedSessions.isEmpty()) {
            return;
        }
        final List<Session> changed = changedSessions.stream()
                .map(NetconfManagementSession::toManagementSession)
                .collect(Collectors.toList());
        final ImmutableList<Session> sessionImmutableList = ImmutableList.copyOf(changed);
        for (NetconfMonitoringService.SessionsListener listener : listeners) {
            listener.onSessionsUpdated(sessionImmutableList);
        }
        changedSessions.clear();
    }

    private void notifySessionUp(final NetconfManagementSession managementSession) {
        Session session = managementSession.toManagementSession();
        for (NetconfMonitoringService.SessionsListener listener : listeners) {
            listener.onSessionStarted(session);
        }
    }

    private void notifySessionDown(final NetconfManagementSession managementSession) {
        Session session = managementSession.toManagementSession();
        for (NetconfMonitoringService.SessionsListener listener : listeners) {
            listener.onSessionEnded(session);
        }
    }

    private void startUpdateSessionStats() {
        if (executor != null) {
            executor.scheduleAtFixedRate(this::updateSessionStats, 1, updateIntervalSeconds, TimeUnit.SECONDS);
            running = true;
        }
    }

    private void stopUpdateSessionStats() {
        if (executor != null) {
            executor.shutdownNow();
            running = false;
        }
    }
}

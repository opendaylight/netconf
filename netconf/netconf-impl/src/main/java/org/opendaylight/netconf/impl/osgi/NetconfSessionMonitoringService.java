/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
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

class NetconfSessionMonitoringService implements SessionListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfSessionMonitoringService.class);

    private final Set<NetconfManagementSession> sessions = Sets.newHashSet();
    private final Set<NetconfManagementSession> changedSessions = Sets.newHashSet();
    private final Set<NetconfMonitoringService.SessionsListener> listeners = Sets.newHashSet();
    private final Optional<ScheduledExecutorService> executor;

    NetconfSessionMonitoringService(Optional<ScheduledThreadPool> schedulingThreadPool, long updateInterval) {
        if (schedulingThreadPool.isPresent() && updateInterval > 0) {
            this.executor =  Optional.of(schedulingThreadPool.get().getExecutor());
            this.executor.get().scheduleAtFixedRate(this::updateSessionStats, 1, updateInterval, TimeUnit.SECONDS);
            LOG.info("/netconf-state/sessions will be updated every {} seconds.", updateInterval);
        } else {
            LOG.info("Scheduling thread pool is present = {}, update interval {}: /netconf-state/sessions won't be updated.",
                    schedulingThreadPool.isPresent(), updateInterval);
            this.executor = Optional.absent();
        }
    }

    synchronized Sessions getSessions() {
        final Collection<Session> managementSessions = Collections2.transform(sessions, NetconfManagementSession::toManagementSession);
        return new SessionsBuilder().setSession(ImmutableList.copyOf(managementSessions)).build();
    }

    @Override
    public synchronized void onSessionUp(final NetconfManagementSession session) {
        LOG.debug("Session {} up", session);
        Preconditions.checkState(!sessions.contains(session), "Session %s was already added", session);
        sessions.add(session);
        notifySessionUp(session);
    }

    @Override
    public synchronized void onSessionDown(final NetconfManagementSession session) {
        LOG.debug("Session {} down", session);
        Preconditions.checkState(sessions.contains(session), "Session %s not present", session);
        sessions.remove(session);
        changedSessions.remove(session);
        notifySessionDown(session);
    }

    @Override
    public synchronized void onSessionEvent(SessionEvent event) {
        changedSessions.add(event.getSession());
    }

    synchronized AutoCloseable registerListener(final NetconfMonitoringService.SessionsListener listener) {
        listeners.add(listener);
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                listeners.remove(listener);
            }
        };
    }

    @Override
    public synchronized void close() throws Exception {
        if (executor.isPresent()) {
            executor.get().shutdownNow();
        }
        listeners.clear();
        sessions.clear();
    }

    private synchronized void updateSessionStats() {
        final List<Session> changed = changedSessions.stream()
                .map(NetconfManagementSession::toManagementSession)
                .collect(Collectors.toList());
        for (NetconfMonitoringService.SessionsListener listener : listeners) {
            listener.onSessionsUpdated(ImmutableList.copyOf(changed));
        }
        changedSessions.clear();
    }

    private void notifySessionUp(NetconfManagementSession managementSession) {
        Session session = managementSession.toManagementSession();
        for (NetconfMonitoringService.SessionsListener listener : listeners) {
            listener.onSessionStarted(session);
        }
    }

    private void notifySessionDown(NetconfManagementSession managementSession) {
        Session session = managementSession.toManagementSession();
        for (NetconfMonitoringService.SessionsListener listener : listeners) {
            listener.onSessionEnded(session);
        }
    }
}

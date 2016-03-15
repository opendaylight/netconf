/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdOrZeroType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEnd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEndBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStart;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStartBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Listens on changes in NetconfState/Sessions datastore and publishes them
 */
public class BaseSessionNotificationPublisher extends BaseNotificationPublisher<Sessions> {

    private final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;

    public BaseSessionNotificationPublisher(BaseNotificationPublisherRegistration baseNotificationPublisherRegistration) {
        super(Sessions.class);
        this.baseNotificationPublisherRegistration = baseNotificationPublisherRegistration;
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> asyncDataChangeEvent) {
        if (asyncDataChangeEvent.getUpdatedSubtree() != null) {
            Preconditions.checkArgument(asyncDataChangeEvent.getUpdatedSubtree() instanceof Sessions);
            final Set<Session> currentSessions = getCurrentSessions(asyncDataChangeEvent);
            final Set<Session> originalSessions = getOriginalSessions(asyncDataChangeEvent);
            publishStartedSessions(Sets.difference(currentSessions, originalSessions));
            publishEndedSessions(Sets.difference(originalSessions, currentSessions));
        }
    }

    private void publishStartedSessions(final Iterable<Session> started) {
        for (final Session session : started) {
            final NetconfSessionStart sessionStart = new NetconfSessionStartBuilder()
                    .setSessionId(new SessionIdOrZeroType(session.getSessionId()))
                    .setSourceHost(session.getSourceHost().getIpAddress())
                    .setUsername(session.getUsername())
                    .build();
            baseNotificationPublisherRegistration.onSessionStarted(sessionStart);
        }
    }

    private void publishEndedSessions(Iterable<Session> ended) {
        for (final Session session : ended) {
            final NetconfSessionEnd sessionEnd = new NetconfSessionEndBuilder()
                    .setSessionId(new SessionIdOrZeroType(session.getSessionId()))
                    .setSourceHost(session.getSourceHost().getIpAddress())
                    .setUsername(session.getUsername())
                    .build();
            baseNotificationPublisherRegistration.onSessionEnded(sessionEnd);
        }
    }

    private static Set<Session> getCurrentSessions(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        final Sessions updatedSubtree = (Sessions) change.getUpdatedSubtree();
        List<Session> sessionList = updatedSubtree != null ? updatedSubtree.getSession() : Collections.emptyList();
        return new HashSet<>(sessionList);
    }

    private static Set<Session> getOriginalSessions(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        final Sessions originalSubtree = (Sessions) change.getOriginalSubtree();
        List<Session> sessionList = originalSubtree != null ? originalSubtree.getSession() : Collections.emptyList();
        return new HashSet<>(sessionList);
    }

    @Override
    public void close() throws Exception {

    }

}

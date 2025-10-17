/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import java.util.List;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectDeleted;
import org.opendaylight.mdsal.binding.api.DataObjectModified;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdOrZeroType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEndBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStartBuilder;
import org.opendaylight.yangtools.binding.DataObjectReference;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens on changes in NetconfState/Sessions/Session datastore and publishes them.
 */
@Component(service = { })
public final class SessionNotificationProducer implements DataTreeChangeListener<Session>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SessionNotificationProducer.class);

    private final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;
    private final Registration sessionListenerRegistration;

    @Activate
    public SessionNotificationProducer(
            @Reference(target = "(type=netconf-notification-manager)") final NetconfNotificationCollector notifManager,
            @Reference final DataBroker dataBroker) {
        baseNotificationPublisherRegistration = notifManager.registerBaseNotificationPublisher();
        sessionListenerRegistration = dataBroker.registerTreeChangeListener(LogicalDatastoreType.OPERATIONAL,
            DataObjectReference.builder(NetconfState.class).child(Sessions.class).child(Session.class).build(), this);
    }

    @Override
    @Deactivate
    public void close() {
        if (baseNotificationPublisherRegistration != null) {
            baseNotificationPublisherRegistration.close();
        }
        if (sessionListenerRegistration != null) {
            sessionListenerRegistration.close();
        }
    }

    @Override
    public void onDataTreeChanged(final List<DataTreeModification<Session>> changes) {
        for (var change : changes) {
            switch (change.getRootNode()) {
                case DataObjectWritten<Session> written:
                    final Session created = written.dataAfter();
                    if (written.dataBefore() == null) {
                        publishStartedSession(created);
                    }
                    break;
                case DataObjectModified<Session> modified:
                    break;
                case DataObjectDeleted<Session> deleted:
                    final Session removed = deleted.dataBefore();
                    if (removed != null) {
                        publishEndedSession(removed);
                    }
                    break;
            }
        }
    }

    private void publishStartedSession(final Session session) {
        baseNotificationPublisherRegistration.onSessionStarted(new NetconfSessionStartBuilder()
            .setSessionId(new SessionIdOrZeroType(session.getSessionId()))
            .setSourceHost(session.getSourceHost().getIpAddress())
            .setUsername(session.getUsername())
            .build());
    }

    private void publishEndedSession(final Session session) {
        baseNotificationPublisherRegistration.onSessionEnded(new NetconfSessionEndBuilder()
            .setSessionId(new SessionIdOrZeroType(session.getSessionId()))
            .setSourceHost(session.getSourceHost().getIpAddress())
            .setUsername(session.getUsername())
            .build());
    }
}

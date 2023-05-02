/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import java.util.Collection;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
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
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
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
    private final ListenerRegistration<?> sessionListenerRegistration;

    @Activate
    public SessionNotificationProducer(
            @Reference(target = "(type=netconf-notification-manager)") final NetconfNotificationCollector notifManager,
            @Reference final DataBroker dataBroker) {
        baseNotificationPublisherRegistration = notifManager.registerBaseNotificationPublisher();
        sessionListenerRegistration = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.builder(NetconfState.class).child(Sessions.class).child(Session.class).build()),
            this);
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
    public void onDataTreeChanged(final Collection<DataTreeModification<Session>> changes) {
        for (DataTreeModification<Session> change : changes) {
            final DataObjectModification<Session> rootNode = change.getRootNode();
            final DataObjectModification.ModificationType modificationType = rootNode.getModificationType();
            switch (modificationType) {
                case WRITE:
                    final Session created = rootNode.getDataAfter();
                    if (created != null && rootNode.getDataBefore() == null) {
                        publishStartedSession(created);
                    }
                    break;
                case DELETE:
                    final Session removed = rootNode.getDataBefore();
                    if (removed != null) {
                        publishEndedSession(removed);
                    }
                    break;
                default:
                    LOG.debug("Received intentionally unhandled type: {}.", modificationType);
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

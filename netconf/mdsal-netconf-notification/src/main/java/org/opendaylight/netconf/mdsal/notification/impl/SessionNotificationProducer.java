/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdOrZeroType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEnd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEndBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStart;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStartBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens on changes in NetconfState/Sessions/Session datastore and publishes them.
 */
public class SessionNotificationProducer extends OperationalDatastoreListener<Session> {

    private static final InstanceIdentifier<Session> SESSION_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class).child(Sessions.class).child(Session.class);
    private static final Logger LOG = LoggerFactory.getLogger(SessionNotificationProducer.class);


    private final BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;
    private final ListenerRegistration<?> sessionListenerRegistration;

    public SessionNotificationProducer(final NetconfNotificationCollector netconfNotificationCollector,
                                       final DataBroker dataBroker) {
        super(SESSION_INSTANCE_IDENTIFIER);

        this.baseNotificationPublisherRegistration = netconfNotificationCollector.registerBaseNotificationPublisher();
        this.sessionListenerRegistration = registerOnChanges(dataBroker);
    }

    @SuppressWarnings("checkstyle:MissingSwitchDefault")
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

    private void publishStartedSession(final DataObject dataObject) {
        checkArgument(dataObject instanceof Session);
        Session session = (Session) dataObject;
        final NetconfSessionStart sessionStart = new NetconfSessionStartBuilder()
                .setSessionId(new SessionIdOrZeroType(session.getSessionId()))
                .setSourceHost(session.getSourceHost().getIpAddress())
                .setUsername(session.getUsername())
                .build();
        baseNotificationPublisherRegistration.onSessionStarted(sessionStart);
    }

    private void publishEndedSession(final DataObject dataObject) {
        checkArgument(dataObject instanceof Session);
        Session session = (Session) dataObject;
        final NetconfSessionEnd sessionEnd = new NetconfSessionEndBuilder()
                .setSessionId(new SessionIdOrZeroType(session.getSessionId()))
                .setSourceHost(session.getSourceHost().getIpAddress())
                .setUsername(session.getUsername())
                .build();
        baseNotificationPublisherRegistration.onSessionEnded(sessionEnd);
    }


    /**
     * Invoked by blueprint.
     */
    public void close() {
        if (baseNotificationPublisherRegistration != null) {
            baseNotificationPublisherRegistration.close();
        }
        if (sessionListenerRegistration != null) {
            sessionListenerRegistration.close();
        }
    }
}

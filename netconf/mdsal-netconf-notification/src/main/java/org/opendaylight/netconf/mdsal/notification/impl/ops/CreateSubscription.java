/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl.ops;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mapping.api.SessionAwareNetconfOperation;
import org.opendaylight.netconf.mdsal.notification.impl.NetconfNotificationManager;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.notifications.NetconfNotificationListener;
import org.opendaylight.netconf.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.notifications.NotificationListenerRegistration;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.netconf.util.messages.SubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Create subscription listens for create subscription requests
 * and registers notification listeners into notification registry.
 * Received notifications are sent to the client right away
 */
public class CreateSubscription extends AbstractSingletonNetconfOperation
        implements SessionAwareNetconfOperation, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CreateSubscription.class);

    static final String CREATE_SUBSCRIPTION = "create-subscription";

    private final NetconfNotificationRegistry notifications;
    private final List<NotificationListenerRegistration> subscriptions = new ArrayList<>();
    private NetconfSession netconfSession;

    public CreateSubscription(final String netconfSessionIdForReporting,
                              final NetconfNotificationRegistry notifications) {
        super(netconfSessionIdForReporting);
        this.notifications = notifications;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document,
                                                       final XmlElement operationElement) throws DocumentedException {
        operationElement.checkName(CREATE_SUBSCRIPTION);
        operationElement.checkNamespace(CreateSubscriptionInput.QNAME.getNamespace().toString());
        // FIXME reimplement using CODEC_REGISTRY and parse everything into generated class instance
        // Binding doesn't support anyxml nodes yet, so filter could not be retrieved
        // xml -> normalized node -> CreateSubscriptionInput conversion could be slower than current approach

        final Optional<XmlElement> filter = operationElement.getOnlyChildElementWithSameNamespaceOptionally("filter");

        // Replay not supported
        final Optional<XmlElement> startTime =
                operationElement.getOnlyChildElementWithSameNamespaceOptionally("startTime");
        checkArgument(!startTime.isPresent(), "StartTime element not yet supported");

        // Stop time not supported
        final Optional<XmlElement> stopTime =
                operationElement.getOnlyChildElementWithSameNamespaceOptionally("stopTime");
        checkArgument(!stopTime.isPresent(), "StopTime element not yet supported");

        final StreamNameType streamNameType = parseStreamIfPresent(operationElement);

        requireNonNull(netconfSession);
        // Premature streams are allowed (meaning listener can register even if no provider is available yet)
        if (!notifications.isStreamAvailable(streamNameType)) {
            LOG.warn("Registering premature stream {}. No publisher available yet for session {}", streamNameType,
                    getNetconfSessionIdForReporting());
        }

        final NotificationListenerRegistration notificationListenerRegistration = notifications
                .registerNotificationListener(streamNameType, new NotificationSubscription(netconfSession, filter));
        subscriptions.add(notificationListenerRegistration);

        return document.createElement(XmlNetconfConstants.OK);
    }

    private static StreamNameType parseStreamIfPresent(final XmlElement operationElement) throws DocumentedException {
        final Optional<XmlElement> stream = operationElement.getOnlyChildElementWithSameNamespaceOptionally("stream");
        return stream.isPresent() ? new StreamNameType(stream.get().getTextContent())
                : NetconfNotificationManager.BASE_STREAM_NAME;
    }

    @Override
    protected String getOperationName() {
        return CREATE_SUBSCRIPTION;
    }

    @Override
    protected String getOperationNamespace() {
        return CreateSubscriptionInput.QNAME.getNamespace().toString();
    }

    @Override
    public void setSession(final NetconfSession session) {
        this.netconfSession = session;
    }

    @Override
    public void close() {
        netconfSession = null;
        // Unregister from notification streams
        for (final NotificationListenerRegistration subscription : subscriptions) {
            subscription.close();
        }
    }

    private static class NotificationSubscription implements NetconfNotificationListener {
        private final NetconfSession currentSession;
        private final Optional<XmlElement> filter;

        NotificationSubscription(final NetconfSession currentSession, final Optional<XmlElement> filter) {
            this.currentSession = currentSession;
            this.filter = filter;
        }

        @Override
        public void onNotification(final StreamNameType stream, final NetconfNotification notification) {
            if (filter.isPresent()) {
                try {
                    final Optional<Document> filtered =
                            SubtreeFilter.applySubtreeNotificationFilter(this.filter.get(), notification.getDocument());
                    if (filtered.isPresent()) {
                        final Date eventTime = notification.getEventTime();
                        currentSession.sendMessage(new NetconfNotification(filtered.get(), eventTime));
                    }
                } catch (DocumentedException e) {
                    LOG.warn("Failed to process notification {}", notification, e);
                    currentSession.sendMessage(notification);
                }
            } else {
                currentSession.sendMessage(notification);
            }
        }
    }
}

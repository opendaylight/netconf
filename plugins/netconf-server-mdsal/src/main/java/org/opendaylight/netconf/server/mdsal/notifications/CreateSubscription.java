/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfSession;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationListener;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.netconf.server.api.operations.SessionAwareNetconfOperation;
import org.opendaylight.netconf.server.spi.SubtreeFilter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Create subscription listens for create subscription requests and registers notification listeners into notification
 * registry. Received notifications are sent to the client right away.
 */
final class CreateSubscription extends AbstractSingletonNetconfOperation
        implements SessionAwareNetconfOperation, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CreateSubscription.class);

    static final @NonNull String CREATE_SUBSCRIPTION = "create-subscription";

    private final List<Registration> subscriptions = new ArrayList<>();
    private final NetconfNotificationRegistry notifications;

    private NetconfSession netconfSession;

    CreateSubscription(final SessionIdType sessionId, final NetconfNotificationRegistry notifications) {
        super(sessionId);
        this.notifications = requireNonNull(notifications);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        operationElement.checkName(CREATE_SUBSCRIPTION);
        operationElement.checkNamespace(CreateSubscriptionInput.QNAME.getNamespace().toString());
        // FIXME reimplement using CODEC_REGISTRY and parse everything into generated class instance
        // Binding doesn't support anyxml nodes yet, so filter could not be retrieved
        // xml -> normalized node -> CreateSubscriptionInput conversion could be slower than current approach

        final Optional<XmlElement> filter = operationElement.getOnlyChildElementWithSameNamespaceOptionally("filter");

        // Replay not supported
        final Optional<XmlElement> startTime =
                operationElement.getOnlyChildElementWithSameNamespaceOptionally("startTime");
        checkArgument(startTime.isEmpty(), "StartTime element not yet supported");

        // Stop time not supported
        final Optional<XmlElement> stopTime =
                operationElement.getOnlyChildElementWithSameNamespaceOptionally("stopTime");
        checkArgument(stopTime.isEmpty(), "StopTime element not yet supported");

        final StreamNameType streamNameType = parseStreamIfPresent(operationElement);

        requireNonNull(netconfSession);
        // Premature streams are allowed (meaning listener can register even if no provider is available yet)
        if (!notifications.isStreamAvailable(streamNameType)) {
            LOG.warn("Registering premature stream {}. No publisher available yet for session {}", streamNameType,
                sessionId().getValue());
        }

        subscriptions.add(notifications.registerNotificationListener(streamNameType,
            new NotificationSubscription(netconfSession, filter)));

        return document.createElement(XmlNetconfConstants.OK);
    }

    private static StreamNameType parseStreamIfPresent(final XmlElement operationElement) throws DocumentedException {
        final Optional<XmlElement> stream = operationElement.getOnlyChildElementWithSameNamespaceOptionally("stream");
        return stream.isPresent() ? new StreamNameType(stream.orElseThrow().getTextContent())
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
        netconfSession = session;
    }

    @Override
    public void close() {
        netconfSession = null;
        // Unregister from notification streams
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
    }

    private static class NotificationSubscription implements NetconfNotificationListener {
        private final NetconfSession currentSession;
        private final XmlElement filter;

        NotificationSubscription(final NetconfSession currentSession, final Optional<XmlElement> filter) {
            this.currentSession = currentSession;
            this.filter = filter.orElse(null);
        }

        @Override
        public void onNotification(final StreamNameType stream, final NotificationMessage notification) {
            if (filter == null) {
                currentSession.sendMessage(notification);
                return;
            }

            try {
                final Optional<Document> filtered =
                        SubtreeFilter.applySubtreeNotificationFilter(filter, notification.getDocument());
                if (filtered.isPresent()) {
                    currentSession.sendMessage(new NotificationMessage(filtered.orElseThrow(),
                        notification.getEventTime()));
                }
            } catch (DocumentedException e) {
                LOG.warn("Failed to process notification {}", notification, e);
                currentSession.sendMessage(notification);
            }
        }
    }
}

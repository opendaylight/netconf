/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.EventSourceStatus;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.EventSourceStatusNotification;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.EventSourceStatusNotificationBuilder;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Topic registration on event-source-status-notification.
 */
class ConnectionNotificationTopicRegistration extends NotificationTopicRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionNotificationTopicRegistration.class);

    public static final SchemaPath EVENT_SOURCE_STATUS_PATH = SchemaPath
            .create(true, QName.create(EventSourceStatusNotification.QNAME, "event-source-status"));
    private static final NodeIdentifier EVENT_SOURCE_STATUS_ARG = NodeIdentifier.create(
            EventSourceStatusNotification.QNAME);

    private final DOMNotificationListener domNotificationListener;

    ConnectionNotificationTopicRegistration(final String sourceName,
                                            final DOMNotificationListener domNotificationListener) {
        super(NotificationSourceType.ConnectionStatusChange, sourceName,
                EVENT_SOURCE_STATUS_PATH.getLastComponent().getNamespace().toString());
        this.domNotificationListener = Preconditions.checkNotNull(domNotificationListener);
        LOG.info("Connection notification source has been initialized.");
        setActive(true);
        setReplaySupported(false);
    }

    @Override
    public void close() {
        if (isActive()) {
            LOG.debug("Connection notification - publish Deactive");
            publishNotification(EventSourceStatus.Deactive);
            notificationTopicMap.clear();
            setActive(false);
        }
    }

    @Override
    void activateNotificationSource() {
        LOG.debug("Connection notification - publish Active");
        publishNotification(EventSourceStatus.Active);
    }

    @Override
    void deActivateNotificationSource() {
        LOG.debug("Connection notification - publish Inactive");
        publishNotification(EventSourceStatus.Inactive);
    }

    @Override
    void reActivateNotificationSource() {
        LOG.debug("Connection notification - reactivate - publish active");
        publishNotification(EventSourceStatus.Active);
    }

    @Override
    boolean registerNotificationTopic(final SchemaPath notificationPath, final TopicId topicId) {
        if (!checkNotificationPath(notificationPath)) {
            LOG.debug("Bad SchemaPath for notification try to register");
            return false;
        }
        Set<TopicId> topicIds = getTopicsForNotification(notificationPath);
        topicIds.add(topicId);
        notificationTopicMap.put(notificationPath, topicIds);
        return true;
    }

    @Override
    synchronized void unRegisterNotificationTopic(final TopicId topicId) {
        List<SchemaPath> notificationPathToRemove = new ArrayList<>();
        for (SchemaPath notifKey : notificationTopicMap.keySet()) {
            Set<TopicId> topicList = notificationTopicMap.get(notifKey);
            if (topicList != null) {
                topicList.remove(topicId);
                if (topicList.isEmpty()) {
                    notificationPathToRemove.add(notifKey);
                }
            }
        }
        for (SchemaPath notifKey : notificationPathToRemove) {
            notificationTopicMap.remove(notifKey);
        }
    }

    private void publishNotification(final EventSourceStatus eventSourceStatus) {

        final EventSourceStatusNotification notification = new EventSourceStatusNotificationBuilder()
                .setStatus(eventSourceStatus).build();
        domNotificationListener.onNotification(createNotification(notification));
    }

    private static DOMNotification createNotification(final EventSourceStatusNotification notification) {
        final ContainerNode cn = Builders.containerBuilder().withNodeIdentifier(EVENT_SOURCE_STATUS_ARG)
                .withChild(encapsulate(notification)).build();
        DOMNotification dn = new DOMNotification() {

            @Override
            public SchemaPath getType() {
                return EVENT_SOURCE_STATUS_PATH;
            }

            @Override
            public ContainerNode getBody() {
                return cn;
            }
        };
        return dn;
    }

    private static DOMSourceAnyxmlNode encapsulate(final EventSourceStatusNotification notification) {
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();

        final Element rootElement = XmlUtil.createElement(doc, "EventSourceStatusNotification",
            Optional.of(EVENT_SOURCE_STATUS_ARG.getNodeType().getNamespace().toString()));

        final Element sourceElement = doc.createElement("status");
        sourceElement.appendChild(doc.createTextNode(notification.getStatus().name()));
        rootElement.appendChild(sourceElement);

        return Builders.anyXmlBuilder().withNodeIdentifier(EVENT_SOURCE_STATUS_ARG)
                .withValue(new DOMSource(rootElement)).build();
    }
}

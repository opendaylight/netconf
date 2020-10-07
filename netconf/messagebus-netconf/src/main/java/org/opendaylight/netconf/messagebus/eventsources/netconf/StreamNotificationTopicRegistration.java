/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Topic registration for notification with specified namespace from stream.
 */
class StreamNotificationTopicRegistration extends NotificationTopicRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(StreamNotificationTopicRegistration.class);

    private final String nodeId;
    private final NetconfEventSource netconfEventSource;
    private final NetconfEventSourceMount mountPoint;
    private final ConcurrentHashMap<SchemaPath, ListenerRegistration<DOMNotificationListener>>
            notificationRegistrationMap = new ConcurrentHashMap<>();
    private final Stream stream;

    /**
     * Creates registration to notification stream.
     *
     * @param stream             stream
     * @param notificationPrefix notifications namespace
     * @param netconfEventSource event source
     */
    StreamNotificationTopicRegistration(final Stream stream, final String notificationPrefix,
                                        final NetconfEventSource netconfEventSource) {
        super(NotificationSourceType.NetconfDeviceStream, stream.getName().getValue(), notificationPrefix);
        this.netconfEventSource = netconfEventSource;
        this.mountPoint = netconfEventSource.getMount();
        this.nodeId = mountPoint.getNode().getNodeId().getValue();
        this.stream = stream;
        setReplaySupported(stream.isReplaySupport());
        setActive(false);
        LOG.info("StreamNotificationTopicRegistration initialized for {}", getStreamName());
    }

    /**
     * Subscribes to notification stream associated with this registration.
     */
    @Override
    void activateNotificationSource() {
        if (!isActive()) {
            LOG.info("Stream {} is not active on node {}. Will subscribe.", this.getStreamName(), this.nodeId);
            final ListenableFuture<? extends DOMRpcResult> result = mountPoint.invokeCreateSubscription(stream);
            try {
                result.get();
                setActive(true);
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Can not subscribe stream {} on node {}", this.getSourceName(), this.nodeId, e);
                setActive(false);
            }
        } else {
            LOG.info("Stream {} is now active on node {}", this.getStreamName(), this.nodeId);
        }
    }

    /**
     * Subscribes to notification stream associated with this registration. If replay is supported, notifications
     * from last
     * received event time will be requested.
     */
    @Override
    void reActivateNotificationSource() {
        if (isActive()) {
            LOG.info("Stream {} is reactivating on node {}.", this.getStreamName(), this.nodeId);
            final ListenableFuture<? extends DOMRpcResult> result = mountPoint.invokeCreateSubscription(stream,
                getLastEventTime());
            try {
                result.get();
                setActive(true);
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Can not resubscribe stream {} on node {}", this.getSourceName(), this.nodeId, e);
                setActive(false);
            }
        }
    }

    @Override
    void deActivateNotificationSource() {
        // no operations need
    }

    private void closeStream() {
        if (isActive()) {
            for (ListenerRegistration<DOMNotificationListener> reg : notificationRegistrationMap.values()) {
                reg.close();
            }
            notificationRegistrationMap.clear();
            notificationTopicMap.clear();
            setActive(false);
        }
    }

    private String getStreamName() {
        return getSourceName();
    }

    @Override
    boolean registerNotificationTopic(final SchemaPath notificationPath, final TopicId topicId) {
        if (!checkNotificationPath(notificationPath)) {
            LOG.debug("Bad SchemaPath for notification try to register");
            return false;
        }

        activateNotificationSource();
        if (!isActive()) {
            LOG.warn("Stream {} is not active, listener for notification {} is not registered.", getStreamName(),
                    notificationPath);
            return false;
        }

        ListenerRegistration<DOMNotificationListener> registration =
                mountPoint.registerNotificationListener(netconfEventSource, notificationPath);
        notificationRegistrationMap.put(notificationPath, registration);
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
            ListenerRegistration<DOMNotificationListener> reg = notificationRegistrationMap.remove(notifKey);
            if (reg != null) {
                reg.close();
            }
        }
    }

    @Override
    public void close() {
        closeStream();
    }

}

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notification topic registration.
 */
abstract class NotificationTopicRegistration implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationTopicRegistration.class);

    public enum NotificationSourceType {
        NetconfDeviceStream,
        ConnectionStatusChange
    }

    private boolean active;
    private final NotificationSourceType notificationSourceType;
    private final String sourceName;
    private final String notificationUrnPrefix;
    private boolean replaySupported;
    private Date lastEventTime;
    protected final ConcurrentHashMap<SchemaPath, Set<TopicId>> notificationTopicMap = new ConcurrentHashMap<>();

    protected NotificationTopicRegistration(final NotificationSourceType notificationSourceType,
            final String sourceName, final String notificationUrnPrefix) {
        this.notificationSourceType = notificationSourceType;
        this.sourceName = sourceName;
        this.notificationUrnPrefix = notificationUrnPrefix;
        this.active = false;
        this.setReplaySupported(false);
    }

    public boolean isActive() {
        return active;
    }

    protected void setActive(final boolean active) {
        this.active = active;
    }

    public NotificationSourceType getNotificationSourceType() {
        return notificationSourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getNotificationUrnPrefix() {
        return notificationUrnPrefix;
    }

    /**
     * Returns registered topics for given notification path.
     *
     * @param notificationPath path
     * @return topicIds
     */
    Set<TopicId> getTopicsForNotification(final SchemaPath notificationPath) {
        final Set<TopicId> topicIds = notificationTopicMap.get(notificationPath);
        return topicIds != null ? topicIds : Sets.newHashSet();
    }

    /**
     * Checks, if notification is from namespace belonging to this registration.
     *
     * @param notificationPath path
     * @return true, if notification belongs to registration namespace
     */
    boolean checkNotificationPath(final SchemaPath notificationPath) {
        if (notificationPath == null) {
            return false;
        }
        String nameSpace = notificationPath.getLastComponent().getNamespace().toString();
        LOG.debug("CheckNotification - name space {} - NotificationUrnPrefix {}", nameSpace,
                getNotificationUrnPrefix());
        return nameSpace.startsWith(getNotificationUrnPrefix());
    }

    Optional<Date> getLastEventTime() {
        return Optional.fromNullable(lastEventTime);
    }

    void setLastEventTime(final Date lastEventTime) {
        this.lastEventTime = lastEventTime;
    }

    abstract void activateNotificationSource();

    abstract void deActivateNotificationSource();

    abstract void reActivateNotificationSource();

    /**
     * Registers associated event source notification to topic.
     *
     * @param notificationPath notification path
     * @param topicId          topic id
     * @return true, if successful
     */
    abstract boolean registerNotificationTopic(SchemaPath notificationPath, TopicId topicId);

    /**
     * Registers associated event source notification to topic.
     *
     * @param topicId topic id
     */
    abstract void unRegisterNotificationTopic(TopicId topicId);

    public boolean isReplaySupported() {
        return replaySupported;
    }

    protected void setReplaySupported(final boolean replaySupported) {
        this.replaySupported = replaySupported;
    }

}

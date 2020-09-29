/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.opendaylight.netconf.mdsal.notification.impl.ops.NotificationsTransformUtil;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.notifications.NetconfNotificationListener;
import org.opendaylight.netconf.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.notifications.NotificationListenerRegistration;
import org.opendaylight.netconf.notifications.NotificationPublisherRegistration;
import org.opendaylight.netconf.notifications.NotificationRegistration;
import org.opendaylight.netconf.notifications.YangLibraryPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.StreamsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEnd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStart;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryUpdate;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  A thread-safe implementation NetconfNotificationRegistry.
 */
@Singleton
public class NetconfNotificationManager implements NetconfNotificationCollector, NetconfNotificationRegistry,
        NetconfNotificationListener, AutoCloseable {

    public static final StreamNameType BASE_STREAM_NAME = new StreamNameType("NETCONF");
    public static final Stream BASE_NETCONF_STREAM = new StreamBuilder()
                .setName(BASE_STREAM_NAME)
                .withKey(new StreamKey(BASE_STREAM_NAME))
                .setReplaySupport(false)
                .setDescription("Default Event Stream")
                .build();

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNotificationManager.class);

    // TODO excessive synchronization provides thread safety but is most likely not optimal
    // (combination of concurrent collections might improve performance)
    // And also calling callbacks from a synchronized block is dangerous
    // since the listeners/publishers can block the whole notification processing

    @GuardedBy("this")
    private final Multimap<StreamNameType, GenericNotificationListenerReg> notificationListeners =
            HashMultimap.create();

    @GuardedBy("this")
    private final Set<NetconfNotificationStreamListener> streamListeners = new HashSet<>();

    @GuardedBy("this")
    private final Map<StreamNameType, Stream> streamMetadata = new HashMap<>();

    @GuardedBy("this")
    private final Multiset<StreamNameType> availableStreams = HashMultiset.create();

    @GuardedBy("this")
    private final Set<GenericNotificationPublisherReg> notificationPublishers = new HashSet<>();
    private final NotificationsTransformUtil transformUtil;

    @Inject
    public NetconfNotificationManager(final NotificationsTransformUtil transformUtil) {
        this.transformUtil = requireNonNull(transformUtil);
    }

    @Override
    public synchronized void onNotification(final StreamNameType stream, final NetconfNotification notification) {
        LOG.debug("Notification of type {} detected", stream);
        if (LOG.isTraceEnabled()) {
            LOG.debug("Notification of type {} detected: {}", stream, notification);
        }

        for (final GenericNotificationListenerReg listenerReg : notificationListeners.get(stream)) {
            listenerReg.getListener().onNotification(stream, notification);
        }
    }

    @Override
    public synchronized NotificationListenerRegistration registerNotificationListener(
            final StreamNameType stream,
            final NetconfNotificationListener listener) {
        requireNonNull(stream);
        requireNonNull(listener);

        LOG.trace("Notification listener registered for stream: {}", stream);

        final GenericNotificationListenerReg genericNotificationListenerReg =
                new GenericNotificationListenerReg(listener) {
            @Override
            public void close() {
                synchronized (NetconfNotificationManager.this) {
                    LOG.trace("Notification listener unregistered for stream: {}", stream);
                    super.close();
                }
            }
        };

        notificationListeners.put(stream, genericNotificationListenerReg);
        return genericNotificationListenerReg;
    }

    @Override
    public synchronized Streams getNotificationPublishers() {
        return new StreamsBuilder().setStream(Maps.uniqueIndex(streamMetadata.values(), Stream::key)).build();
    }

    @Override
    public synchronized boolean isStreamAvailable(final StreamNameType streamNameType) {
        return availableStreams.contains(streamNameType);
    }

    @Override
    public synchronized NotificationRegistration registerStreamListener(
            final NetconfNotificationStreamListener listener) {
        streamListeners.add(listener);

        // Notify about all already available
        for (final Stream availableStream : streamMetadata.values()) {
            listener.onStreamRegistered(availableStream);
        }

        return () -> {
            synchronized (NetconfNotificationManager.this) {
                streamListeners.remove(listener);
            }
        };
    }

    @Override
    public synchronized void close() {
        // Unregister all listeners
        for (final GenericNotificationListenerReg genericNotificationListenerReg : notificationListeners.values()) {
            genericNotificationListenerReg.close();
        }
        notificationListeners.clear();

        // Unregister all publishers
        for (final GenericNotificationPublisherReg notificationPublisher : notificationPublishers) {
            notificationPublisher.close();
        }
        notificationPublishers.clear();

        // Clear stream Listeners
        streamListeners.clear();
    }

    @Override
    public synchronized NotificationPublisherRegistration registerNotificationPublisher(final Stream stream) {
        final StreamNameType streamName = requireNonNull(stream).getName();

        LOG.debug("Notification publisher registered for stream: {}", streamName);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Notification publisher registered for stream: {}", stream);
        }

        if (streamMetadata.containsKey(streamName)) {
            LOG.warn("Notification stream {} already registered as: {}. Will be reused", streamName,
                    streamMetadata.get(streamName));
        } else {
            streamMetadata.put(streamName, stream);
        }

        availableStreams.add(streamName);

        final GenericNotificationPublisherReg genericNotificationPublisherReg =
                new GenericNotificationPublisherReg(this, streamName) {
            @Override
            public void close() {
                synchronized (NetconfNotificationManager.this) {
                    super.close();
                }
            }
        };

        notificationPublishers.add(genericNotificationPublisherReg);

        notifyStreamAdded(stream);
        return genericNotificationPublisherReg;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void unregisterNotificationPublisher(
            final StreamNameType streamName,
            final GenericNotificationPublisherReg genericNotificationPublisherReg) {
        availableStreams.remove(streamName);
        notificationPublishers.remove(genericNotificationPublisherReg);

        LOG.debug("Notification publisher unregistered for stream: {}", streamName);

        // Notify stream listeners if all publishers are gone and also clear metadata for stream
        if (!isStreamAvailable(streamName)) {
            LOG.debug("Notification stream: {} became unavailable", streamName);
            streamMetadata.remove(streamName);
            notifyStreamRemoved(streamName);
        }
    }

    private synchronized void notifyStreamAdded(final Stream stream) {
        for (final NetconfNotificationStreamListener streamListener : streamListeners) {
            streamListener.onStreamRegistered(stream);
        }
    }

    private synchronized void notifyStreamRemoved(final StreamNameType stream) {
        for (final NetconfNotificationStreamListener streamListener : streamListeners) {
            streamListener.onStreamUnregistered(stream);
        }
    }

    @Override
    public BaseNotificationPublisherRegistration registerBaseNotificationPublisher() {
        final NotificationPublisherRegistration notificationPublisherRegistration =
                registerNotificationPublisher(BASE_NETCONF_STREAM);
        return new BaseNotificationPublisherReg(transformUtil, notificationPublisherRegistration);
    }

    @Override
    public YangLibraryPublisherRegistration registerYangLibraryPublisher() {
        final NotificationPublisherRegistration notificationPublisherRegistration =
                registerNotificationPublisher(BASE_NETCONF_STREAM);
        return new YangLibraryPublisherReg(transformUtil, notificationPublisherRegistration);
    }

    private static class GenericNotificationPublisherReg implements NotificationPublisherRegistration {
        private NetconfNotificationManager baseListener;
        private final StreamNameType registeredStream;

        GenericNotificationPublisherReg(final NetconfNotificationManager baseListener,
                                        final StreamNameType registeredStream) {
            this.baseListener = baseListener;
            this.registeredStream = registeredStream;
        }

        @Override
        public void close() {
            baseListener.unregisterNotificationPublisher(registeredStream, this);
            baseListener = null;
        }

        @Override
        public void onNotification(final StreamNameType stream, final NetconfNotification notification) {
            checkState(baseListener != null, "Already closed");
            checkArgument(stream.equals(registeredStream));
            baseListener.onNotification(stream, notification);
        }
    }

    private static class BaseNotificationPublisherReg implements BaseNotificationPublisherRegistration {

        static final SchemaPath CAPABILITY_CHANGE_SCHEMA_PATH = SchemaPath.create(true, NetconfCapabilityChange.QNAME);
        static final SchemaPath SESSION_START_PATH = SchemaPath.create(true, NetconfSessionStart.QNAME);
        static final SchemaPath SESSION_END_PATH = SchemaPath.create(true, NetconfSessionEnd.QNAME);

        private final NotificationPublisherRegistration baseRegistration;
        private final NotificationsTransformUtil transformUtil;

        BaseNotificationPublisherReg(final NotificationsTransformUtil transformUtil,
                final NotificationPublisherRegistration baseRegistration) {
            this.transformUtil = requireNonNull(transformUtil);
            this.baseRegistration = baseRegistration;
        }

        @Override
        public void close() {
            baseRegistration.close();
        }

        private NetconfNotification serializeNotification(final Notification notification, final SchemaPath path) {
            return transformUtil.transform(notification, path);
        }

        @Override
        public void onCapabilityChanged(final NetconfCapabilityChange capabilityChange) {
            baseRegistration.onNotification(BASE_STREAM_NAME,
                    serializeNotification(capabilityChange, CAPABILITY_CHANGE_SCHEMA_PATH));
        }

        @Override
        public void onSessionStarted(final NetconfSessionStart start) {
            baseRegistration.onNotification(BASE_STREAM_NAME, serializeNotification(start, SESSION_START_PATH));
        }

        @Override
        public void onSessionEnded(final NetconfSessionEnd end) {
            baseRegistration.onNotification(BASE_STREAM_NAME, serializeNotification(end, SESSION_END_PATH));
        }
    }

    private static class YangLibraryPublisherReg implements YangLibraryPublisherRegistration {
        static final SchemaPath YANG_LIBRARY_CHANGE_PATH = SchemaPath.create(true, YangLibraryChange.QNAME);
        static final SchemaPath YANG_LIBRARY_UPDATE_PATH = SchemaPath.create(true, YangLibraryUpdate.QNAME);

        private final NotificationPublisherRegistration baseRegistration;
        private final NotificationsTransformUtil transformUtil;

        YangLibraryPublisherReg(final NotificationsTransformUtil transformUtil,
                final NotificationPublisherRegistration baseRegistration) {
            this.transformUtil = requireNonNull(transformUtil);
            this.baseRegistration = baseRegistration;
        }

        @Override
        @Deprecated
        public void onYangLibraryChange(final YangLibraryChange yangLibraryChange) {
            baseRegistration.onNotification(BASE_STREAM_NAME,
                transformUtil.transform(yangLibraryChange, YANG_LIBRARY_CHANGE_PATH));
        }

        @Override
        public void onYangLibraryUpdate(YangLibraryUpdate yangLibraryUpdate) {
            baseRegistration.onNotification(BASE_STREAM_NAME,
                    transformUtil.transform(yangLibraryUpdate, YANG_LIBRARY_UPDATE_PATH));
        }

        @Override
        public void close() {
            baseRegistration.close();
        }
    }

    private class GenericNotificationListenerReg implements NotificationListenerRegistration {
        private final NetconfNotificationListener listener;

        GenericNotificationListenerReg(final NetconfNotificationListener listener) {
            this.listener = listener;
        }

        public NetconfNotificationListener getListener() {
            return listener;
        }

        @Override
        public void close() {
            notificationListeners.remove(BASE_STREAM_NAME, this);
        }
    }
}

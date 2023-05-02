/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.dom.codec.spi.BindingDOMCodecFactory;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector.NetconfNotificationStreamListener;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationListener;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.server.api.notifications.NotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.notifications.YangLibraryPublisherRegistration;
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
import org.opendaylight.yangtools.concepts.AbstractObjectRegistration;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  A thread-safe implementation NetconfNotificationRegistry.
 */
@Singleton
@Component(service = { NetconfNotificationCollector.class, NetconfNotificationRegistry.class }, immediate = true,
           property = "type=netconf-notification-manager")
public final class NetconfNotificationManager implements NetconfNotificationCollector, NetconfNotificationRegistry,
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
    private final Multimap<StreamNameType, ListenerReg> notificationListeners = HashMultimap.create();

    @GuardedBy("this")
    private final Set<StreamListenerReg> streamListeners = new HashSet<>();

    @GuardedBy("this")
    private final Map<StreamNameType, Stream> streamMetadata = new HashMap<>();

    @GuardedBy("this")
    private final Multiset<StreamNameType> availableStreams = HashMultiset.create();

    @GuardedBy("this")
    private final Set<PublisherReg> notificationPublishers = new HashSet<>();
    private final NotificationsTransformUtil transformUtil;

    @Inject
    @Activate
    public NetconfNotificationManager(@Reference final YangParserFactory parserFactory,
            @Reference final BindingRuntimeGenerator generator, @Reference final BindingDOMCodecFactory codecFactory)
                throws YangParserException {
        transformUtil = new NotificationsTransformUtil(parserFactory, generator, codecFactory);
    }

    @PreDestroy
    @Deactivate
    @Override
    public synchronized void close() {
        // Unregister all listeners
        // Use new list to avoid ConcurrentModificationException when the registration removes itself
        List.copyOf(notificationListeners.values()).forEach(ListenerReg::close);
        notificationListeners.clear();

        // Unregister all publishers
        // Use new list to avoid ConcurrentModificationException when the registration removes itself
        List.copyOf(notificationPublishers).forEach(PublisherReg::close);
        notificationPublishers.clear();

        // Clear stream Listeners
        streamListeners.clear();
    }

    @Override
    public synchronized void onNotification(final StreamNameType stream, final NotificationMessage notification) {
        LOG.debug("Notification of type {} detected", stream);
        if (LOG.isTraceEnabled()) {
            LOG.debug("Notification of type {} detected: {}", stream, notification);
        }

        for (var listenerReg : notificationListeners.get(stream)) {
            listenerReg.getInstance().onNotification(stream, notification);
        }
    }

    @Override
    public synchronized Registration registerNotificationListener(final StreamNameType stream,
            final NetconfNotificationListener listener) {
        final var reg = new ListenerReg(listener, stream);
        LOG.trace("Notification listener registered for stream: {}", stream);
        notificationListeners.put(stream, reg);
        return reg;
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
    public synchronized Registration registerStreamListener(final NetconfNotificationStreamListener listener) {
        final var reg = new StreamListenerReg(listener);
        streamListeners.add(reg);

        // Notify about all already available
        for (var availableStream : streamMetadata.values()) {
            listener.onStreamRegistered(availableStream);
        }

        return reg;
    }

    @Override
    public synchronized NotificationPublisherRegistration registerNotificationPublisher(final Stream stream) {
        final var streamName = requireNonNull(stream).getName();
        final var reg = new PublisherReg(this, streamName);

        LOG.debug("Notification publisher registered for stream: {}", streamName);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Notification publisher registered for stream: {}", stream);
        }

        final var prev = streamMetadata.putIfAbsent(streamName, stream);
        if (prev != null) {
            LOG.warn("Notification stream {} already registered as: {}. Will be reused", streamName, prev);
        }

        availableStreams.add(streamName);

        notificationPublishers.add(reg);

        notifyStreamAdded(stream);
        return reg;
    }

    private synchronized void unregisterNotificationPublisher(final StreamNameType streamName, final PublisherReg reg) {
        availableStreams.remove(streamName);
        notificationPublishers.remove(reg);

        LOG.debug("Notification publisher unregistered for stream: {}", streamName);

        // Notify stream listeners if all publishers are gone and also clear metadata for stream
        if (!isStreamAvailable(streamName)) {
            LOG.debug("Notification stream: {} became unavailable", streamName);
            streamMetadata.remove(streamName);
            notifyStreamRemoved(streamName);
        }
    }

    private synchronized void notifyStreamAdded(final Stream stream) {
        for (var streamListener : streamListeners) {
            streamListener.getInstance().onStreamRegistered(stream);
        }
    }

    private synchronized void notifyStreamRemoved(final StreamNameType stream) {
        for (var streamListener : streamListeners) {
            streamListener.getInstance().onStreamUnregistered(stream);
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

    private static final class PublisherReg extends AbstractRegistration implements NotificationPublisherRegistration {
        private final StreamNameType registeredStream;

        private NetconfNotificationManager manager;

        PublisherReg(final NetconfNotificationManager manager, final StreamNameType registeredStream) {
            this.manager = requireNonNull(manager);
            this.registeredStream = requireNonNull(registeredStream);
        }

        @Override
        public void onNotification(final StreamNameType stream, final NotificationMessage notification) {
            checkArgument(stream.equals(registeredStream),
                "Registered on %s, cannot publish to %s", registeredStream, stream);
            checkState(notClosed(), "Already closed");
            manager.onNotification(stream, notification);
        }

        @Override
        protected void removeRegistration() {
            manager.unregisterNotificationPublisher(registeredStream, this);
            manager = null;
        }
    }

    private abstract static class AbstractTransformedRegistration implements Registration {
        private final NotificationPublisherRegistration delegate;
        private final NotificationsTransformUtil transformUtil;

        AbstractTransformedRegistration(final NotificationsTransformUtil transformUtil,
                final NotificationPublisherRegistration delegate) {
            this.transformUtil = requireNonNull(transformUtil);
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public final void close() {
            delegate.close();
        }

        final void publishNotification(final Notification<?> notification, final Absolute path) {
            delegate.onNotification(BASE_STREAM_NAME, transformUtil.transform(notification, path));
        }
    }

    private static class BaseNotificationPublisherReg extends AbstractTransformedRegistration
            implements BaseNotificationPublisherRegistration {
        private static final Absolute CAPABILITY_CHANGE_SCHEMA_PATH = Absolute.of(NetconfCapabilityChange.QNAME);
        private static final Absolute SESSION_START_PATH = Absolute.of(NetconfSessionStart.QNAME);
        private static final Absolute SESSION_END_PATH = Absolute.of(NetconfSessionEnd.QNAME);

        BaseNotificationPublisherReg(final NotificationsTransformUtil transformUtil,
                final NotificationPublisherRegistration delegate) {
            super(transformUtil, delegate);
        }

        @Override
        public void onCapabilityChanged(final NetconfCapabilityChange capabilityChange) {
            publishNotification(capabilityChange, CAPABILITY_CHANGE_SCHEMA_PATH);
        }

        @Override
        public void onSessionStarted(final NetconfSessionStart start) {
            publishNotification(start, SESSION_START_PATH);
        }

        @Override
        public void onSessionEnded(final NetconfSessionEnd end) {
            publishNotification(end, SESSION_END_PATH);
        }
    }

    private static class YangLibraryPublisherReg extends AbstractTransformedRegistration
            implements YangLibraryPublisherRegistration {
        private static final Absolute YANG_LIBRARY_CHANGE_PATH = Absolute.of(YangLibraryChange.QNAME);
        private static final Absolute YANG_LIBRARY_UPDATE_PATH = Absolute.of(YangLibraryUpdate.QNAME);

        YangLibraryPublisherReg(final NotificationsTransformUtil transformUtil,
                final NotificationPublisherRegistration delegate) {
            super(transformUtil, delegate);
        }

        @Override
        @Deprecated
        public void onYangLibraryChange(final YangLibraryChange yangLibraryChange) {
            publishNotification(yangLibraryChange, YANG_LIBRARY_CHANGE_PATH);
        }

        @Override
        public void onYangLibraryUpdate(final YangLibraryUpdate yangLibraryUpdate) {
            publishNotification(yangLibraryUpdate, YANG_LIBRARY_UPDATE_PATH);
        }
    }

    private final class ListenerReg extends AbstractObjectRegistration<NetconfNotificationListener> {
        private final StreamNameType stream;

        ListenerReg(final @NonNull NetconfNotificationListener instance, final @NonNull StreamNameType stream) {
            super(instance);
            this.stream = requireNonNull(stream);
        }

        @Override
        protected void removeRegistration() {
            synchronized (NetconfNotificationManager.this) {
                LOG.trace("Notification listener unregistered for stream: {}", stream);
                notificationListeners.remove(stream, this);
            }
        }
    }

    private final class StreamListenerReg extends AbstractObjectRegistration<NetconfNotificationStreamListener> {
        StreamListenerReg(final @NonNull NetconfNotificationStreamListener instance) {
            super(instance);
        }

        @Override
        protected void removeRegistration() {
            synchronized (NetconfNotificationManager.this) {
                streamListeners.remove(this);
            }
        }
    }
}

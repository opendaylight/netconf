/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation, removal and searching for {@link ListenerAdapter} or
 * {@link NotificationListenerAdapter} listeners.
 */
public final class ListenersBroker {
    private static final Logger LOG = LoggerFactory.getLogger(ListenersBroker.class);
    private static ListenersBroker listenersBroker;

    private final StampedLock dataChangeListenersLock = new StampedLock();
    private final StampedLock notificationListenersLock = new StampedLock();
    private final BiMap<String, ListenerAdapter> dataChangeListeners = HashBiMap.create();
    private final BiMap<String, NotificationListenerAdapter> notificationListeners = HashBiMap.create();

    private ListenersBroker() {
    }

    /**
     * Creation of the singleton listeners broker.
     *
     * @return Reusable instance of {@link ListenersBroker}.
     */
    public static synchronized ListenersBroker getInstance() {
        if (listenersBroker == null) {
            listenersBroker = new ListenersBroker();
        }
        return listenersBroker;
    }

    /**
     * Returns set of all data-change-event streams.
     */
    public Set<String> getDataChangeStreams() {
        final long stamp = dataChangeListenersLock.readLock();
        try {
            return ImmutableSet.copyOf(dataChangeListeners.keySet());
        } finally {
            dataChangeListenersLock.unlockRead(stamp);
        }
    }

    /**
     * Returns set of all notification streams.
     */
    public Set<String> getNotificationStreams() {
        final long stamp = notificationListenersLock.readLock();
        try {
            return ImmutableSet.copyOf(notificationListeners.keySet());
        } finally {
            notificationListenersLock.unlockRead(stamp);
        }
    }

    /**
     * Gets {@link ListenerAdapter} specified by stream identification.
     *
     * @param streamName Stream name.
     * @return {@link ListenerAdapter} specified by stream name wrapped in {@link Optional} or {@link Optional#empty()}
     *     if listener with specified stream name doesn't exist.
     */
    public Optional<ListenerAdapter> getDataChangeListenerFor(final String streamName) {
        final long stamp = dataChangeListenersLock.readLock();
        try {
            final ListenerAdapter listenerAdapter = dataChangeListeners.get(requireNonNull(streamName));
            return Optional.ofNullable(listenerAdapter);
        } finally {
            dataChangeListenersLock.unlockRead(stamp);
        }
    }

    /**
     * Gets {@link NotificationListenerAdapter} specified by stream name.
     *
     * @param streamName Stream name.
     * @return {@link NotificationListenerAdapter} specified by stream name wrapped in {@link Optional}
     *     or {@link Optional#empty()} if listener with specified stream name doesn't exist.
     */
    public Optional<NotificationListenerAdapter> getNotificationListenerFor(final String streamName) {
        final long stamp = notificationListenersLock.readLock();
        try {
            final NotificationListenerAdapter listenerAdapter = notificationListeners.get(requireNonNull(streamName));
            return Optional.ofNullable(listenerAdapter);
        } finally {
            notificationListenersLock.unlockRead(stamp);
        }
    }

    /**
     * Get listener for stream-name.
     *
     * @param streamName Stream name.
     * @return {@link NotificationListenerAdapter} or {@link ListenerAdapter} object wrapped in {@link Optional}
     *     or {@link Optional#empty()} if listener with specified stream name doesn't exist.
     */
    public Optional<BaseListenerInterface> getListenerFor(final String streamName) {
        if (streamName.startsWith(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
            return getNotificationListenerFor(streamName).map(Function.identity());
        } else if (streamName.startsWith(RestconfStreamsConstants.DATA_SUBSCRIPTION)) {
            return getDataChangeListenerFor(streamName).map(Function.identity());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Creates new {@link ListenerAdapter} listener using input stream name and path if such listener
     * hasn't been created yet.
     *
     * @param path       Path to data in data repository.
     * @param streamName Stream name.
     * @param outputType Specific type of output for notifications - XML or JSON.
     * @return Created or existing data-change listener adapter.
     */
    public ListenerAdapter registerDataChangeListener(final YangInstanceIdentifier path, final String streamName,
            final NotificationOutputType outputType) {
        requireNonNull(path);
        requireNonNull(streamName);
        requireNonNull(outputType);

        final long stamp = dataChangeListenersLock.writeLock();
        try {
            return dataChangeListeners.computeIfAbsent(streamName, stream -> new ListenerAdapter(
                    path, stream, outputType));
        } finally {
            dataChangeListenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Creates new {@link NotificationDefinition} listener using input stream name and schema path
     * if such listener haven't been created yet.
     *
     * @param schemaPath Schema path of YANG notification structure.
     * @param streamName Stream name.
     * @param outputType Specific type of output for notifications - XML or JSON.
     * @return Created or existing notification listener adapter.
     */
    public NotificationListenerAdapter registerNotificationListener(final SchemaPath schemaPath,
            final String streamName, final NotificationOutputType outputType) {
        requireNonNull(schemaPath);
        requireNonNull(streamName);
        requireNonNull(outputType);

        final long stamp = notificationListenersLock.writeLock();
        try {
            return notificationListeners.computeIfAbsent(streamName, stream -> new NotificationListenerAdapter(
                    schemaPath, stream, outputType.getName()));
        } finally {
            notificationListenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Removal and closing of all data-change-event and notification listeners.
     */
    public synchronized void removeAndCloseAllListeners() {
        final long stampNotifications = notificationListenersLock.writeLock();
        final long stampDataChanges = dataChangeListenersLock.writeLock();
        try {
            removeAndCloseAllDataChangeListenersTemplate();
            removeAndCloseAllNotificationListenersTemplate();
        } finally {
            dataChangeListenersLock.unlockWrite(stampDataChanges);
            notificationListenersLock.unlockWrite(stampNotifications);
        }
    }

    /**
     * Closes and removes all data-change listeners.
     */
    public void removeAndCloseAllDataChangeListeners() {
        final long stamp = dataChangeListenersLock.writeLock();
        try {
            removeAndCloseAllDataChangeListenersTemplate();
        } finally {
            dataChangeListenersLock.unlockWrite(stamp);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeAndCloseAllDataChangeListenersTemplate() {
        dataChangeListeners.values()
                .forEach(listenerAdapter -> {
                    try {
                        listenerAdapter.close();
                    } catch (final Exception exception) {
                        LOG.error("Failed to close data-change listener {}.", listenerAdapter, exception);
                        throw new IllegalStateException(String.format("Failed to close data-change listener %s.",
                                listenerAdapter), exception);
                    }
                });
        dataChangeListeners.clear();
    }

    /**
     * Closes and removes all notification listeners.
     */
    public void removeAndCloseAllNotificationListeners() {
        final long stamp = notificationListenersLock.writeLock();
        try {
            removeAndCloseAllNotificationListenersTemplate();
        } finally {
            notificationListenersLock.unlockWrite(stamp);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeAndCloseAllNotificationListenersTemplate() {
        notificationListeners.values()
                .forEach(listenerAdapter -> {
                    try {
                        listenerAdapter.close();
                    } catch (final Exception exception) {
                        LOG.error("Failed to close notification listener {}.", listenerAdapter, exception);
                        throw new IllegalStateException(String.format("Failed to close notification listener %s.",
                                listenerAdapter), exception);
                    }
                });
        notificationListeners.clear();
    }

    /**
     * Removes and closes data-change listener of type {@link ListenerAdapter} specified in parameter.
     *
     * @param listener Listener to be closed and removed.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void removeAndCloseDataChangeListener(final ListenerAdapter listener) {
        final long stamp = dataChangeListenersLock.writeLock();
        try {
            removeAndCloseDataChangeListenerTemplate(listener);
        } catch (final Exception exception) {
            LOG.error("Data-change listener {} cannot be closed.", listener, exception);
        } finally {
            dataChangeListenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Removes and closes data-change listener of type {@link ListenerAdapter} specified in parameter.
     *
     * @param listener Listener to be closed and removed.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeAndCloseDataChangeListenerTemplate(final ListenerAdapter listener) {
        try {
            requireNonNull(listener).close();
            if (dataChangeListeners.inverse().remove(listener) == null) {
                LOG.warn("There isn't any data-change event stream that would match listener adapter {}.", listener);
            }
        } catch (final Exception exception) {
            LOG.error("Data-change listener {} cannot be closed.", listener, exception);
            throw new IllegalStateException(String.format(
                    "Data-change listener %s cannot be closed.",
                    listener), exception);
        }
    }

    /**
     * Removes and closes notification listener of type {@link NotificationListenerAdapter} specified in parameter.
     *
     * @param listener Listener to be closed and removed.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void removeAndCloseNotificationListener(final NotificationListenerAdapter listener) {
        final long stamp = notificationListenersLock.writeLock();
        try {
            removeAndCloseNotificationListenerTemplate(listener);
        } catch (final Exception exception) {
            LOG.error("Notification listener {} cannot be closed.", listener, exception);
        } finally {
            notificationListenersLock.unlockWrite(stamp);
        }
    }

    @SuppressWarnings({"checkstyle:IllegalCatch"})
    private void removeAndCloseNotificationListenerTemplate(NotificationListenerAdapter listener) {
        try {
            requireNonNull(listener).close();
            if (notificationListeners.inverse().remove(listener) == null) {
                LOG.warn("There isn't any notification stream that would match listener adapter {}.", listener);
            }
        } catch (final Exception exception) {
            LOG.error("Notification listener {} cannot be closed.", listener, exception);
            throw new IllegalStateException(String.format(
                    "Notification listener %s cannot be closed.", listener),
                    exception);
        }
    }

    /**
     * Removal and closing of general listener (data-change or notification listener).
     *
     * @param listener Listener to be closed and removed from cache.
     */
    void removeAndCloseListener(final BaseListenerInterface listener) {
        requireNonNull(listener);
        if (listener instanceof ListenerAdapter) {
            removeAndCloseDataChangeListener((ListenerAdapter) listener);
        } else if (listener instanceof NotificationListenerAdapter) {
            removeAndCloseNotificationListener((NotificationListenerAdapter) listener);
        }
    }

    /**
     * Creates string representation of stream name from URI. Removes slash from URI in start and end positions,
     * and optionally {@link RestconfConstants#BASE_URI_PATTERN} prefix.
     *
     * @param uri URI for creation of stream name.
     * @return String representation of stream name.
     */
    public static String createStreamNameFromUri(final String uri) {
        String result = requireNonNull(uri);
        while (true) {
            if (result.startsWith(RestconfConstants.BASE_URI_PATTERN)) {
                result = result.substring(RestconfConstants.BASE_URI_PATTERN.length());
            } else if (result.startsWith("/")) {
                result = result.substring(1);
            } else {
                break;
            }
        }
        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    @VisibleForTesting
    public synchronized void setDataChangeListeners(final Map<String, ListenerAdapter> listenerAdapterCollection) {
        final long stamp = dataChangeListenersLock.writeLock();
        try {
            dataChangeListeners.clear();
            dataChangeListeners.putAll(listenerAdapterCollection);
        } finally {
            dataChangeListenersLock.unlockWrite(stamp);
        }
    }
}
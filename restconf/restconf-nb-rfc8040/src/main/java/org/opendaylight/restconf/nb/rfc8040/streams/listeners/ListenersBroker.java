/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
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
    private final Map<String, ListenerAdapter> dataChangeListeners = new HashMap<>();
    private final Map<String, NotificationListenerAdapter> notificationListeners = new HashMap<>();

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
     * Returns list of all data-change-event streams.
     */
    public Set<String> getDataChangeStreams() {
        final long stamp = dataChangeListenersLock.readLock();
        try {
            return dataChangeListeners.keySet();
        } finally {
            dataChangeListenersLock.unlockRead(stamp);
        }
    }

    /**
     * Returns list of all notification streams.
     */
    public Set<String> getNotificationStreams() {
        final long stamp = notificationListenersLock.readLock();
        try {
            return notificationListeners.keySet();
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
            final ListenerAdapter listenerAdapter = dataChangeListeners.get(Preconditions.checkNotNull(streamName));
            return listenerAdapter == null ? Optional.empty() : Optional.of(listenerAdapter);
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
            final NotificationListenerAdapter listenerAdapter = notificationListeners.get(
                    Preconditions.checkNotNull(streamName));
            return listenerAdapter == null ? Optional.empty() : Optional.of(listenerAdapter);
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
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(streamName);
        Preconditions.checkNotNull(outputType);

        final long stamp = dataChangeListenersLock.writeLock();
        try {
            if (!dataChangeListeners.containsKey(streamName)) {
                final ListenerAdapter listener = new ListenerAdapter(path, streamName, outputType);
                dataChangeListeners.put(streamName, listener);
                return listener;
            } else {
                return dataChangeListeners.get(streamName);
            }
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
        Preconditions.checkNotNull(schemaPath);
        Preconditions.checkNotNull(streamName);
        Preconditions.checkNotNull(outputType);

        final long stamp = notificationListenersLock.writeLock();
        try {
            if (!notificationListeners.containsKey(streamName)) {
                final NotificationListenerAdapter listenerAdapter = new NotificationListenerAdapter(
                        schemaPath, streamName, outputType.getName());
                notificationListeners.put(streamName, listenerAdapter);
                return listenerAdapter;
            } else {
                return notificationListeners.get(streamName);
            }
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
                        throw new IllegalStateException(String.format(
                                "Failed to close data-change listener %s.",
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
                        LOG.error("Failed to close notification listener {}.",
                                listenerAdapter,
                                exception);
                        throw new IllegalStateException(String.format(
                                "Failed to close notification listener %s.",
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
        final long stamp = dataChangeListenersLock.writeLock();
        try {
            Preconditions.checkNotNull(listener).close();
            final Optional<String> foundStreamIdentification = dataChangeListeners.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(listener))
                    .map(Map.Entry::getKey)
                    .findAny();
            if (foundStreamIdentification.isPresent()) {
                dataChangeListeners.remove(foundStreamIdentification.get());
            } else {
                LOG.warn("There isn't any data-change event stream that would match listener adapter {}.", listener);
            }
        } catch (final Exception exception) {
            LOG.error("Data-change listener {} cannot be closed.", listener, exception);
            throw new IllegalStateException(String.format(
                    "Data-change listener %s cannot be closed.",
                    listener), exception);
        } finally {
            dataChangeListenersLock.unlockWrite(stamp);
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
            Preconditions.checkNotNull(listener).close();
            final Optional<Map.Entry<String, NotificationListenerAdapter>> foundStreamData
                    = notificationListeners.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(listener))
                    .findAny();
            if (foundStreamData.isPresent()) {
                notificationListeners.remove(foundStreamData.get().getKey());
            } else {
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
        Preconditions.checkNotNull(listener);
        if (listener instanceof ListenerAdapter) {
            removeAndCloseDataChangeListener((ListenerAdapter) listener);
        } else if (listener instanceof NotificationListenerAdapter) {
            removeAndCloseNotificationListener((NotificationListenerAdapter) listener);
        }
    }

    /**
     * Creates string representation of stream name from URI. Removes slash from URI in start and end positions.
     *
     * @param uri URI for creation of stream name.
     * @return String representation of stream name.
     */
    public static String createStreamNameFromUri(final String uri) {
        String result = Preconditions.checkNotNull(uri);
        if (result.startsWith("/")) {
            result = result.substring(1);
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
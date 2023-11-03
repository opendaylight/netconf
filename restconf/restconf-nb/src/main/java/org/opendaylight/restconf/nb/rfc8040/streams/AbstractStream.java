/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.xml.xpath.XPathExpressionException;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.ReceiveEventsParams;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base superclass for all stream types.
 */
abstract class AbstractStream<T> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractStream.class);

    private final EventFormatterFactory<T> formatterFactory;
    private final NotificationOutputType outputType;
    private final String streamName;
    protected final @NonNull ListenersBroker listenersBroker;

    @GuardedBy("this")
    private final Subscribers<T> subscribers = Subscribers.empty();
    @GuardedBy("this")
    private Registration registration;

    // FIXME: NETCONF-1102: this should be tied to a subscriber
    private @NonNull EventFormatter<T> formatter;

    // FIXME: these really should not live here
    protected DatabindProvider databindProvider;
    private DOMDataBroker dataBroker;

    AbstractStream(final String streamName, final NotificationOutputType outputType,
            final EventFormatterFactory<T> formatterFactory, final ListenersBroker listenersBroker) {
        this.streamName = requireNonNull(streamName);
        checkArgument(!streamName.isEmpty());

        this.outputType = requireNonNull(outputType);
        this.formatterFactory = requireNonNull(formatterFactory);
        this.listenersBroker = requireNonNull(listenersBroker);
        formatter = formatterFactory.emptyFormatter();
    }

    /**
     * Get name of stream.
     *
     * @return Stream name.
     */
    public final String getStreamName() {
        return streamName;
    }

    /**
     * Get output type.
     *
     * @return Output type (JSON or XML).
     */
    final String getOutputType() {
        return outputType.getName();
    }

    /**
     * Checks if exists at least one {@link StreamSessionHandler} subscriber.
     *
     * @return {@code true} if exist at least one {@link StreamSessionHandler} subscriber, {@code false} otherwise.
     */
    final synchronized boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    /**
     * Return all subscribers of listener.
     *
     * @return Set of all subscribers.
     */
    final synchronized Set<StreamSessionHandler> getSubscribers() {
        return new HashSet<>(subscribers);
    }

    @Override
    public final synchronized void close() throws InterruptedException, ExecutionException {
        if (registration != null) {
            registration.close();
            registration = null;
        }
        deleteDataInDS().get();
        subscribers.clear();
    }

    /**
     * Registers {@link StreamSessionHandler} subscriber.
     *
     * @param subscriber SSE or WS session handler.
     */
    synchronized void addSubscriber(final StreamSessionHandler subscriber) {
        final boolean isConnected = subscriber.isConnected();
        checkState(isConnected);
        LOG.debug("Subscriber {} is added.", subscriber);
        subscribers.add(subscriber);
    }

    /**
     * Removes {@link StreamSessionHandler} subscriber.
     *
     * @param subscriber SSE or WS session handler.
     */
    synchronized void removeSubscriber(final StreamSessionHandler subscriber) {
        subscribers.remove(subscriber);
        LOG.debug("Subscriber {} is removed", subscriber);
        if (!hasSubscribers()) {
            listenersBroker.removeAndCloseListener(this);
        }
    }

    /**
     * Set query parameters for listener.
     *
     * @param params NotificationQueryParams to use.
     */
    public final void setQueryParams(final ReceiveEventsParams params) {
        final var startTime = params.startTime();
        if (startTime != null) {
            throw new RestconfDocumentedException("Stream " + streamName + " does not support replay",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final var leafNodes = params.leafNodesOnly();
        final var skipData = params.skipNotificationData();
        final var changedLeafNodes = params.changedLeafNodesOnly();
        final var childNodes = params.childNodesOnly();

        final var textParams = new TextParameters(
            leafNodes != null && leafNodes.value(),
            skipData != null && skipData.value(),
            changedLeafNodes != null && changedLeafNodes.value(),
            childNodes != null && childNodes.value());

        final var filter = params.filter();
        final var filterValue = filter == null ? null : filter.paramValue();

        final EventFormatter<T> newFormatter;
        if (filterValue != null && !filterValue.isEmpty()) {
            try {
                newFormatter = formatterFactory.getFormatter(textParams, filterValue);
            } catch (XPathExpressionException e) {
                throw new IllegalArgumentException("Failed to get filter", e);
            }
        } else {
            newFormatter = formatterFactory.getFormatter(textParams);
        }

        // Single assign
        formatter = newFormatter;
    }

    final @NonNull EventFormatter<T> formatter() {
        return formatter;
    }

    /**
     * Sets {@link Registration} registration.
     *
     * @param registration a listener registration registration.
     */
    @Holding("this")
    final void setRegistration(final Registration registration) {
        this.registration = requireNonNull(registration);
    }

    /**
     * Checks if {@link Registration} registration exists.
     *
     * @return {@code true} if exists, {@code false} otherwise.
     */
    @Holding("this")
    final boolean isListening() {
        return registration != null;
    }

    /**
     * Post data to subscribed SSE session handlers.
     *
     * @param data Data of incoming notifications.
     */
    synchronized void post(final String data) {
        final Iterator<StreamSessionHandler> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            final StreamSessionHandler subscriber = iterator.next();
            final boolean isConnected = subscriber.isConnected();
            if (isConnected) {
                subscriber.sendDataMessage(data);
                LOG.debug("Data was sent to subscriber {} on connection {}:", this, subscriber);
            } else {
                // removal is probably not necessary, because it will be removed explicitly soon after invocation of
                // onWebSocketClosed(..) in handler; but just to be sure ...
                iterator.remove();
                LOG.debug("Subscriber for {} was removed - web-socket session is not open.", this);
            }
        }
    }

    /**
     * Data broker for delete data in DS on close().
     *
     * @param dataBroker creating new write transaction for delete data on close
     * @param databindProvider for formatting notifications
     */
    @SuppressWarnings("checkstyle:hiddenField")
    // FIXME: this is pure lifecycle nightmare just because ...
    public void setCloseVars(final DOMDataBroker dataBroker, final DatabindProvider databindProvider) {
        this.dataBroker = dataBroker;
        this.databindProvider = databindProvider;
    }

    /**
     * Delete data in DS.
     */
    // FIXME: here we touch datastore, which probably should be done by whoever instantiated us or created the resource,
    //        or they should be giving us the transaction
    private ListenableFuture<?> deleteDataInDS() {
        final var wTx = dataBroker.newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.OPERATIONAL, RestconfStateStreams.restconfStateStreamPath(streamName));
        return wTx.commit();
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("stream-name", streamName).add("output-type", getOutputType());
    }
}

/*
 * Copyright (c) 2014, 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.ClusteredDOMDataTreeChangeListener;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ListenerAdapter} is responsible to track events, which occurred by changing data in data source.
 */
public class ListenerAdapter extends AbstractCommonSubscriber<YangInstanceIdentifier, Collection<DataTreeCandidate>>
        implements ClusteredDOMDataTreeChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapter.class);
    private static final DataTreeCandidateFormatterFactory JSON_FORMATTER_FACTORY =
            JSONDataTreeCandidateFormatter.createFactory(JSONCodecFactorySupplier.RFC7951);

    /**
     * Creates new {@link ListenerAdapter} listener specified by path and stream name and register for subscribing.
     *
     * @param path       Path to data in data store.
     * @param streamName The name of the stream.
     * @param outputType Type of output on notification (JSON, XML).
     */
    @VisibleForTesting
    public ListenerAdapter(final YangInstanceIdentifier path, final String streamName,
            final NotificationOutputType outputType) {
        super(path.getLastPathArgument().getNodeType(), streamName, path, outputType, getFormatterFactory(outputType));
    }

    private static DataTreeCandidateFormatterFactory getFormatterFactory(final NotificationOutputType outputType) {
        switch (outputType) {
            case JSON:
                return JSON_FORMATTER_FACTORY;
            case XML:
                return XMLDataTreeCandidateFormatter.FACTORY;
            default:
                throw new IllegalArgumentException("Unsupported outputType" + outputType);
        }
    }

    @Override
    public void onInitialData() {
        // No-op
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onDataTreeChanged(final List<DataTreeCandidate> dataTreeCandidates) {
        final Instant now = Instant.now();
        if (!checkStartStop(now)) {
            return;
        }

        final Optional<String> maybeData;
        try {
            maybeData = formatter().eventData(schemaHandler.get(), dataTreeCandidates, now, getLeafNodesOnly(),
                    isSkipNotificationData());
        } catch (final Exception e) {
            LOG.error("Failed to process notification {}",
                    dataTreeCandidates.stream().map(Object::toString).collect(Collectors.joining(",")), e);
            return;
        }

        if (maybeData.isPresent()) {
            post(maybeData.get());
        }
    }

    /**
     * Get path pointed to data in data store.
     *
     * @return Path pointed to data in data store.
     */
    public YangInstanceIdentifier getPath() {
        return path();
    }

    /**
     * Register data change listener in DOM data broker and set it to listener on stream.
     *
     * @param domDataBroker data broker for register data change listener
     * @param datastore     {@link LogicalDatastoreType}
     */
    public final synchronized void listen(final DOMDataBroker domDataBroker, final LogicalDatastoreType datastore) {
        if (!isListening()) {
            final DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
            if (changeService == null) {
                throw new UnsupportedOperationException("DOMDataBroker does not support the DOMDataTreeChangeService");
            }

            setRegistration(changeService.registerDataTreeChangeListener(
                new DOMDataTreeIdentifier(datastore, getPath()), this));
        }
    }
}

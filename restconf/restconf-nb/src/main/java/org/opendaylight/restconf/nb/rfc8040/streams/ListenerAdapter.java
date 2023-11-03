/*
 * Copyright (c) 2014, 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
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
public class ListenerAdapter extends AbstractStream<Collection<DataTreeCandidate>>
        implements ClusteredDOMDataTreeChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapter.class);
    private static final DataTreeCandidateFormatterFactory JSON_FORMATTER_FACTORY =
            JSONDataTreeCandidateFormatter.createFactory(JSONCodecFactorySupplier.RFC7951);

    private final @NonNull LogicalDatastoreType datastore;
    private final @NonNull YangInstanceIdentifier path;

    /**
     * Creates new {@link ListenerAdapter} listener specified by path and stream name and register for subscribing.
     *
     * @param path       Path to data in data store.
     * @param streamName The name of the stream.
     * @param outputType Type of output on notification (JSON, XML).
     */
    ListenerAdapter(final String streamName, final NotificationOutputType outputType,
            final ListenersBroker listenersBroker, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path) {
        super(streamName, outputType, getFormatterFactory(outputType), listenersBroker);
        this.datastore = requireNonNull(datastore);
        this.path = requireNonNull(path);
    }

    private static DataTreeCandidateFormatterFactory getFormatterFactory(final NotificationOutputType outputType) {
        return switch (outputType) {
            case JSON -> JSON_FORMATTER_FACTORY;
            case XML -> XMLDataTreeCandidateFormatter.FACTORY;
        };
    }

    @Override
    public void onInitialData() {
        // No-op
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onDataTreeChanged(final List<DataTreeCandidate> dataTreeCandidates) {
        final var now = Instant.now();
        final String data;
        try {
            data = formatter().eventData(databindProvider.currentContext().modelContext(), dataTreeCandidates, now);
        } catch (final Exception e) {
            LOG.error("Failed to process notification {}",
                    dataTreeCandidates.stream().map(Object::toString).collect(Collectors.joining(",")), e);
            return;
        }
        if (data != null) {
            post(data);
        }
    }

    /**
     * Get path pointed to data in data store.
     *
     * @return Path pointed to data in data store.
     */
    public YangInstanceIdentifier getPath() {
        return path;
    }

    /**
     * Register data change listener in DOM data broker and set it to listener on stream.
     *
     * @param domDataBroker data broker for register data change listener
     */
    public final synchronized void listen(final DOMDataBroker domDataBroker) {
        if (!isListening()) {
            final var changeService = domDataBroker.getExtensions().getInstance(DOMDataTreeChangeService.class);
            if (changeService == null) {
                throw new UnsupportedOperationException("DOMDataBroker does not support the DOMDataTreeChangeService");
            }

            setRegistration(changeService.registerDataTreeChangeListener(
                new DOMDataTreeIdentifier(datastore, path), this));
        }
    }

    @Override
    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("path", path));
    }
}

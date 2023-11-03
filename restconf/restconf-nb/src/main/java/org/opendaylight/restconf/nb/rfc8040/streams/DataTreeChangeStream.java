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
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.ClusteredDOMDataTreeChangeListener;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;

/**
 * A {@link RestconfStream} reporting changes on a particular data tree.
 */
public final class DataTreeChangeStream extends RestconfStream<List<DataTreeCandidate>>
        implements ClusteredDOMDataTreeChangeListener {
    private static final ImmutableMap<EncodingName, DataTreeCandidateFormatterFactory> ENCODINGS = ImmutableMap.of(
        EncodingName.RFC8040_JSON, JSONDataTreeCandidateFormatter.FACTORY,
        EncodingName.RFC8040_XML, XMLDataTreeCandidateFormatter.FACTORY);

    private final DatabindProvider databindProvider;
    private final @NonNull LogicalDatastoreType datastore;
    private final @NonNull YangInstanceIdentifier path;

    DataTreeChangeStream(final ListenersBroker listenersBroker, final String name,
            final NotificationOutputType outputType, final DatabindProvider databindProvider,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        super(listenersBroker, name, ENCODINGS, outputType);
        this.databindProvider = requireNonNull(databindProvider);
        this.datastore = requireNonNull(datastore);
        this.path = requireNonNull(path);
    }

    @Override
    public void onInitialData() {
        // No-op
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onDataTreeChanged(final List<DataTreeCandidate> dataTreeCandidates) {
        sendDataMessage(databindProvider.currentContext().modelContext(), dataTreeCandidates, Instant.now());
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
    public synchronized void listen(final DOMDataBroker domDataBroker) {
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

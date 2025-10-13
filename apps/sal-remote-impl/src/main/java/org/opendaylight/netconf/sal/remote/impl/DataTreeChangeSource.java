/*
 * Copyright (c) 2014, 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.remote.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker.DataTreeChangeExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeListener;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.restconf.server.api.MonitoringEncoding;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;

/**
 * A {@link RestconfStream} reporting changes on a particular data tree.
 */
@VisibleForTesting
public final class DataTreeChangeSource extends Source<List<DataTreeCandidate>> {
    private static final ImmutableMap<MonitoringEncoding, DataTreeCandidateFormatterFactory> ENCODINGS =
        ImmutableMap.of(
            MonitoringEncoding.JSON, JSONDataTreeCandidateFormatter.FACTORY,
            MonitoringEncoding.XML, XMLDataTreeCandidateFormatter.FACTORY);

    private final @NonNull DataTreeChangeExtension changeService;
    private final @NonNull DatabindProvider databindProvider;
    private final @NonNull LogicalDatastoreType datastore;
    private final @NonNull YangInstanceIdentifier path;

    public DataTreeChangeSource(final DatabindProvider databindProvider, final DataTreeChangeExtension changeService,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        super(ENCODINGS);
        this.databindProvider = requireNonNull(databindProvider);
        this.changeService = requireNonNull(changeService);
        this.datastore = requireNonNull(datastore);
        this.path = requireNonNull(path);
    }

    @Override
    protected Registration start(final Sink<List<DataTreeCandidate>> sink) {
        return changeService.registerTreeChangeListener(DOMDataTreeIdentifier.of(datastore, path),
            new DOMDataTreeChangeListener() {
                @Override
                public void onDataTreeChanged(final List<DataTreeCandidate> changes) {
                    // FIXME: format one change at a time?
                    sink.publish(databindProvider.currentDatabind().modelContext(), changes, Instant.now());
                }

                @Override
                public void onInitialData() {
                    // No-op
                }
            });
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("path", path));
    }
}

/*
 * Copyright (c) 2014, 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.ClusteredDOMDataTreeChangeListener;
import org.opendaylight.restconf.common.formatters.DataTreeCandidateFormatter;
import org.opendaylight.restconf.common.formatters.DataTreeCandidateFormatterFactory;
import org.opendaylight.restconf.common.formatters.JSONDataTreeCandidateFormatter;
import org.opendaylight.restconf.common.formatters.XMLDataTreeCandidateFormatter;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ListenerAdapter} is responsible to track events, which occurred by changing data in data source.
 */
public class ListenerAdapter extends AbstractCommonSubscriber implements ClusteredDOMDataTreeChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapter.class);
//    private static final String DATA_CHANGE_EVENT = "data-change-event";
    private static final String PATH = "path";
//    private static final String OPERATION = "operation";
    private static final DataTreeCandidateFormatterFactory JSON_FORMATTER_FACTORY =
            JSONDataTreeCandidateFormatter.createFactory(JSONCodecFactorySupplier.RFC7951);

    private final YangInstanceIdentifier path;
    private final String streamName;
    private final NotificationOutputType outputType;

    @VisibleForTesting DataTreeCandidateFormatter formatter;

    /**
     * Creates new {@link ListenerAdapter} listener specified by path and stream name and register for subscribing.
     *
     * @param path       Path to data in data store.
     * @param streamName The name of the stream.
     * @param outputType Type of output on notification (JSON, XML).
     */
    ListenerAdapter(final YangInstanceIdentifier path, final String streamName,
            final NotificationOutputType outputType) {
        setLocalNameOfPath(path.getLastPathArgument().getNodeType().getLocalName());

        this.outputType = requireNonNull(outputType);
        this.path = requireNonNull(path);
        this.streamName = requireNonNull(streamName);
        checkArgument(!streamName.isEmpty());

        formatter = getFormatterFactory().getFormatter();
    }

    private DataTreeCandidateFormatterFactory getFormatterFactory() {
        switch (outputType) {
            case JSON:
                return JSON_FORMATTER_FACTORY;
            case XML:
                return XMLDataTreeCandidateFormatter.FACTORY;
            default:
                throw new IllegalArgumentException(("Unsupported outputType" + outputType));
        }
    }

    private DataTreeCandidateFormatter getFormatter(final String filter) throws XPathExpressionException {
        final DataTreeCandidateFormatterFactory factory = getFormatterFactory();
        return filter == null || filter.isEmpty() ? factory.getFormatter() : factory.getFormatter(filter);
    }

    @Override
    public void setQueryParams(final Instant start, final Instant stop, final String filter,
                               final boolean leafNodesOnly, final boolean skipNotificationData) {
        super.setQueryParams(start, stop, filter, leafNodesOnly, skipNotificationData);
        try {
            this.formatter = getFormatter(filter);
        } catch (final XPathExpressionException e) {
            throw new IllegalArgumentException("Failed to get filter", e);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onDataTreeChanged(final Collection<DataTreeCandidate> dataTreeCandidates) {
        final Instant now = Instant.now();
        if (!checkStartStop(now, this)) {
            return;
        }

        final Optional<String> maybeData;
        try {
            maybeData = formatter.eventData(schemaHandler.get(), dataTreeCandidates, now, getLeafNodesOnly(),
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
     * Gets the name of the stream.
     *
     * @return The name of the stream.
     */
    @Override
    public String getStreamName() {
        return this.streamName;
    }

    @Override
    public String getOutputType() {
        return this.outputType.getName();
    }

    /**
     * Get path pointed to data in data store.
     *
     * @return Path pointed to data in data store.
     */
    public YangInstanceIdentifier getPath() {
        return this.path;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add(PATH, path)
                .add("stream-name", streamName)
                .add("output-type", outputType)
                .toString();
    }
}

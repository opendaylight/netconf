/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfStreamsSubscriptionService}.
 */
@Path("/")
public class RestconfStreamsSubscriptionServiceImpl implements RestconfStreamsSubscriptionService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsSubscriptionServiceImpl.class);
    private static final QName LOCATION_QNAME =
        QName.create("subscribe:to:notification", "2016-10-28", "location").intern();
    private static final NodeIdentifier LOCATION_NODEID = NodeIdentifier.create(LOCATION_QNAME);
    private static final QName NOTIFI_QNAME = QName.create(LOCATION_QNAME, "notifi").intern();
    private static final YangInstanceIdentifier LOCATION_PATH =
        YangInstanceIdentifier.create(NodeIdentifier.create(NOTIFI_QNAME), LOCATION_NODEID);

    private final SubscribeToStreamUtil streamUtils;
    private final HandlersHolder handlersHolder;

    /**
     * Initialize holder of handlers with holders as parameters.
     *
     * @param dataBroker {@link DOMDataBroker}
     * @param notificationService {@link DOMNotificationService}
     * @param schemaHandler
     *             handler of {@link SchemaContext}
     * @param transactionChainHandler
     *             handler of {@link DOMTransactionChain}
     * @param configuration
     *             configuration for restconf {@link Configuration}}
     */
    public RestconfStreamsSubscriptionServiceImpl(final DOMDataBroker dataBroker,
            final DOMNotificationService notificationService, final SchemaContextHandler schemaHandler,
            final Configuration configuration) {
        this.handlersHolder = new HandlersHolder(dataBroker, notificationService, schemaHandler);
        streamUtils = configuration.isUseSSE() ? SubscribeToStreamUtil.serverSentEvents()
                : SubscribeToStreamUtil.webSockets();
    }

    @Override
    public NormalizedNodeContext subscribeToStream(final String identifier, final UriInfo uriInfo) {
        final NotificationQueryParams notificationQueryParams = NotificationQueryParams.fromUriInfo(uriInfo);

        final URI response;
        if (identifier.contains(RestconfStreamsConstants.DATA_SUBSCRIPTION)) {
            response = streamUtils.subscribeToDataStream(identifier, uriInfo, notificationQueryParams, handlersHolder);
        } else if (identifier.contains(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
            response = streamUtils.subscribeToYangStream(identifier, uriInfo, notificationQueryParams, handlersHolder);
        } else {
            final String msg = "Bad type of notification of sal-remote";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg);
        }

        // prepare new header with location
        final Map<String, Object> headers = new HashMap<>();
        headers.put("Location", response);

        // prepare node with value of location
        return new NormalizedNodeContext(prepareIIDSubsStreamOutput(handlersHolder.getSchemaHandler()),
            ImmutableLeafNodeBuilder.create()
                .withNodeIdentifier(LOCATION_NODEID)
                .withValue(response.toString())
                .build(), headers);
    }

    /**
     * Prepare InstanceIdentifierContext for Location leaf.
     *
     * @param schemaHandler Schema context handler.
     * @return InstanceIdentifier of Location leaf.
     */
    private static InstanceIdentifierContext<?> prepareIIDSubsStreamOutput(final SchemaContextHandler schemaHandler) {
        final Optional<Module> module = schemaHandler.get().findModule(NOTIFI_QNAME.getModule());
        checkState(module.isPresent());
        final DataSchemaNode notify = module.get().dataChildByName(NOTIFI_QNAME);
        checkState(notify instanceof ContainerSchemaNode, "Unexpected non-container %s", notify);
        final DataSchemaNode location = ((ContainerSchemaNode) notify).dataChildByName(LOCATION_QNAME);
        checkState(location != null, "Missing location");

        return new InstanceIdentifierContext<SchemaNode>(LOCATION_PATH, location, null, schemaHandler.get());
    }

    /**
     * Holder of all handlers for notifications.
     */
    // FIXME: why do we even need this class?!
    public static final class HandlersHolder {
        private final DOMDataBroker dataBroker;
        private final DOMNotificationService notificationService;
        private final SchemaContextHandler schemaHandler;

        private HandlersHolder(final DOMDataBroker dataBroker, final DOMNotificationService notificationService,
                final SchemaContextHandler schemaHandler) {
            this.dataBroker = dataBroker;
            this.notificationService = notificationService;
            this.schemaHandler = schemaHandler;
        }

        /**
         * Get {@link DOMDataBroker}.
         *
         * @return the dataBroker
         */
        public DOMDataBroker getDataBroker() {
            return this.dataBroker;
        }

        /**
         * Get {@link DOMNotificationService}.
         *
         * @return the notificationService
         */
        public DOMNotificationService getNotificationServiceHandler() {
            return this.notificationService;
        }

        /**
         * Get {@link SchemaContextHandler}.
         *
         * @return the schemaHandler
         */
        public SchemaContextHandler getSchemaHandler() {
            return this.schemaHandler;
        }
    }

    /**
     * Parser and holder of query paramteres from uriInfo for notifications.
     */
    public static final class NotificationQueryParams {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .appendValue(ChronoField.YEAR, 4).appendLiteral('-')
                .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
                .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('T')
                .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':')
                .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                .appendOffset("+HH:MM", "Z").toFormatter();

        private final Instant start;
        private final Instant stop;
        private final String filter;
        private final boolean skipNotificationData;

        private NotificationQueryParams(final Instant start, final Instant stop, final String filter,
                final boolean skipNotificationData) {
            this.start = start == null ? Instant.now() : start;
            this.stop = stop;
            this.filter = filter;
            this.skipNotificationData = skipNotificationData;
        }

        static NotificationQueryParams fromUriInfo(final UriInfo uriInfo) {
            Instant start = null;
            boolean startTimeUsed = false;
            Instant stop = null;
            boolean stopTimeUsed = false;
            String filter = null;
            boolean filterUsed = false;
            boolean skipNotificationDataUsed = false;
            boolean skipNotificationData = false;

            for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
                switch (entry.getKey()) {
                    case "start-time":
                        if (!startTimeUsed) {
                            startTimeUsed = true;
                            start = parseDateFromQueryParam(entry);
                        } else {
                            throw new RestconfDocumentedException("Start-time parameter can be used only once.");
                        }
                        break;
                    case "stop-time":
                        if (!stopTimeUsed) {
                            stopTimeUsed = true;
                            stop = parseDateFromQueryParam(entry);
                        } else {
                            throw new RestconfDocumentedException("Stop-time parameter can be used only once.");
                        }
                        break;
                    case "filter":
                        if (!filterUsed) {
                            filterUsed = true;
                            filter = entry.getValue().iterator().next();
                        }
                        break;
                    case "odl-skip-notification-data":
                        if (!skipNotificationDataUsed) {
                            skipNotificationDataUsed = true;
                            skipNotificationData = Boolean.parseBoolean(entry.getValue().iterator().next());
                        } else {
                            throw new RestconfDocumentedException(
                                    "Odl-skip-notification-data parameter can be used only once.");
                        }
                        break;
                    default:
                        throw new RestconfDocumentedException(
                                "Bad parameter used with notifications: " + entry.getKey());
                }
            }
            if (!startTimeUsed && stopTimeUsed) {
                throw new RestconfDocumentedException("Stop-time parameter has to be used with start-time parameter.");
            }

            return new NotificationQueryParams(start, stop, filter, skipNotificationData);
        }


        /**
         * Parse input of query parameters - start-time or stop-time - from {@link DateAndTime} format
         * to {@link Instant} format.
         *
         * @param entry Start-time or stop-time as string in {@link DateAndTime} format.
         * @return Parsed {@link Instant} by entry.
         */
        private static Instant parseDateFromQueryParam(final Entry<String, List<String>> entry) {
            final DateAndTime event = new DateAndTime(entry.getValue().iterator().next());
            final String value = event.getValue();
            final TemporalAccessor accessor;
            try {
                accessor = FORMATTER.parse(value);
            } catch (final DateTimeParseException e) {
                throw new RestconfDocumentedException("Cannot parse of value in date: " + value, e);
            }
            return Instant.from(accessor);
        }

        /**
         * Get start-time query parameter.
         *
         * @return start-time
         */
        public @NonNull Instant getStart() {
            return start;
        }

        /**
         * Get stop-time query parameter.
         *
         * @return stop-time
         */
        public Optional<Instant> getStop() {
            return Optional.ofNullable(stop);
        }

        /**
         * Get filter query parameter.
         *
         * @return filter
         */
        public Optional<String> getFilter() {
            return Optional.ofNullable(filter);
        }

        /**
         * Check whether this query should notify changes without data.
         *
         * @return true if this query should notify about changes with  data
         */
        public boolean isSkipNotificationData() {
            return skipNotificationData;
        }
    }
}

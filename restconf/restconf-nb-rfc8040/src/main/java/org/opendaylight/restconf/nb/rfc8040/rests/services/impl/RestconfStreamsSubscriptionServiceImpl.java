/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.SubscribeToStreamUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfStreamsSubscriptionService}.
 *
 */
public class RestconfStreamsSubscriptionServiceImpl implements RestconfStreamsSubscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsSubscriptionServiceImpl.class);

    private final HandlersHolder handlersHolder;
    private final String schema;

    /**
     * Initialize holder of handlers with holders as parameters.
     *
     * @param domDataBrokerHandler
     *             handler of {@link DOMDataBroker}
     * @param notificationServiceHandler
     *             handler of {@link DOMNotificationService}
     * @param schemaHandler
     *             handler of {@link SchemaContext}
     * @param transactionChainHandler
     *             handler of {@link DOMTransactionChain}
     */
    public RestconfStreamsSubscriptionServiceImpl(final DOMDataBrokerHandler domDataBrokerHandler,
            final NotificationServiceHandler notificationServiceHandler, final SchemaContextHandler schemaHandler,
            final TransactionChainHandler transactionChainHandler, final String schema) {
        this.handlersHolder = new HandlersHolder(domDataBrokerHandler, notificationServiceHandler,
                transactionChainHandler, schemaHandler);
        this.schema = schema;
    }

    @Override
    public NormalizedNodeContext subscribeToStream(final String identifier, final UriInfo uriInfo) {
        final NotificationQueryParams notificationQueryParams = NotificationQueryParams.fromUriInfo(uriInfo);

        URI response = null;
        if (identifier.contains(RestconfStreamsConstants.DATA_SUBSCR)) {
            response = SubscribeToStreamUtil.notifiDataStream(identifier, uriInfo, notificationQueryParams,
                    this.handlersHolder, schema);
        } else if (identifier.contains(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
            response = SubscribeToStreamUtil.notifYangStream(identifier, uriInfo, notificationQueryParams,
                    this.handlersHolder, schema);
        }

        if (response != null) {
            // prepare node with value of location
            final InstanceIdentifierContext<?> iid =
                    SubscribeToStreamUtil.prepareIIDSubsStreamOutput(this.handlersHolder.getSchemaHandler());
            final NormalizedNodeAttrBuilder<NodeIdentifier, Object, LeafNode<Object>> builder =
                    ImmutableLeafNodeBuilder.create().withValue(response.toString());
            builder.withNodeIdentifier(
                    NodeIdentifier.create(QName.create("subscribe:to:notification", "2016-10-28", "location")));

            // prepare new header with location
            final Map<String, Object> headers = new HashMap<>();
            headers.put("Location", response);

            return new NormalizedNodeContext(iid, builder.build(), headers);
        }

        final String msg = "Bad type of notification of sal-remote";
        LOG.warn(msg);
        throw new RestconfDocumentedException(msg);
    }

    /**
     * Holder of all handlers for notifications.
     */
    public final class HandlersHolder {

        private final DOMDataBrokerHandler domDataBrokerHandler;
        private final NotificationServiceHandler notificationServiceHandler;
        private final TransactionChainHandler transactionChainHandler;
        private final SchemaContextHandler schemaHandler;

        private HandlersHolder(final DOMDataBrokerHandler domDataBrokerHandler,
                final NotificationServiceHandler notificationServiceHandler,
                final TransactionChainHandler transactionChainHandler, final SchemaContextHandler schemaHandler) {
            this.domDataBrokerHandler = domDataBrokerHandler;
            this.notificationServiceHandler = notificationServiceHandler;
            this.transactionChainHandler = transactionChainHandler;
            this.schemaHandler = schemaHandler;
        }

        /**
         * Get {@link DOMDataBrokerHandler}.
         *
         * @return the domDataBrokerHandler
         */
        public DOMDataBrokerHandler getDomDataBrokerHandler() {
            return this.domDataBrokerHandler;
        }

        /**
         * Get {@link NotificationServiceHandler}.
         *
         * @return the notificationServiceHandler
         */
        public NotificationServiceHandler getNotificationServiceHandler() {
            return this.notificationServiceHandler;
        }

        /**
         * Get {@link TransactionChainHandler}.
         *
         * @return the transactionChainHandler
         */
        public TransactionChainHandler getTransactionChainHandler() {
            return this.transactionChainHandler;
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
     *
     */
    public static final class NotificationQueryParams {

        private final Instant start;
        private final Instant stop;
        private final String filter;

        private NotificationQueryParams(final Instant start, final Instant stop, final String filter) {
            this.start = start == null ? Instant.now() : start;
            this.stop = stop;
            this.filter = filter;
        }

        static NotificationQueryParams fromUriInfo(final UriInfo uriInfo) {
            Instant start = null;
            boolean startTimeUsed = false;
            Instant stop = null;
            boolean stopTimeUsed = false;
            String filter = null;
            boolean filterUsed = false;

            for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
                switch (entry.getKey()) {
                    case "start-time":
                        if (!startTimeUsed) {
                            startTimeUsed = true;
                            start = SubscribeToStreamUtil.parseDateFromQueryParam(entry);
                        } else {
                            throw new RestconfDocumentedException("Start-time parameter can be used only once.");
                        }
                        break;
                    case "stop-time":
                        if (!stopTimeUsed) {
                            stopTimeUsed = true;
                            stop = SubscribeToStreamUtil.parseDateFromQueryParam(entry);
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
                    default:
                        throw new RestconfDocumentedException(
                                "Bad parameter used with notifications: " + entry.getKey());
                }
            }
            if (!startTimeUsed && stopTimeUsed) {
                throw new RestconfDocumentedException("Stop-time parameter has to be used with start-time parameter.");
            }

            return new NotificationQueryParams(start, stop, filter);
        }

        /**
         * Get start-time query parameter.
         *
         * @return start-time
         */
        @Nonnull
        public Instant getStart() {
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
    }

}

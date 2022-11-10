/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.net.URI;
import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContextProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
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

    private final SubscribeToStreamUtil streamUtils;
    private final HandlersHolder handlersHolder;

    /**
     * Initialize holder of handlers with holders as parameters.
     *
     * @param dataBroker {@link DOMDataBroker}
     * @param notificationService {@link DOMNotificationService}
     * @param databindContextProvider a {@link DatabindContextProvider}
     * @param configuration configuration for RESTCONF {@link Configuration}}
     */
    public RestconfStreamsSubscriptionServiceImpl(final DOMDataBroker dataBroker,
            final DOMNotificationService notificationService, final DatabindContextProvider databindContextProvider,
            final Configuration configuration) {
        handlersHolder = new HandlersHolder(dataBroker, notificationService, databindContextProvider);
        streamUtils = configuration.isUseSSE() ? SubscribeToStreamUtil.serverSentEvents()
                : SubscribeToStreamUtil.webSockets();
    }

    @Override
    public NormalizedNodePayload subscribeToStream(final String identifier, final UriInfo uriInfo) {
        final NotificationQueryParams params = QueryParams.newNotificationQueryParams(uriInfo);

        final URI response;
        if (identifier.contains(RestconfStreamsConstants.DATA_SUBSCRIPTION)) {
            response = streamUtils.subscribeToDataStream(identifier, uriInfo, params, handlersHolder);
        } else if (identifier.contains(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
            response = streamUtils.subscribeToYangStream(identifier, uriInfo, params, handlersHolder);
        } else {
            final String msg = "Bad type of notification of sal-remote";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg);
        }

        // prepare node with value of location
        return NormalizedNodePayload.ofLocation(
            prepareIIDSubsStreamOutput(handlersHolder.getSchemaHandler().currentDatabindContext().modelContext()),
            LOCATION_NODEID, response);
    }

    /**
     * Prepare InstanceIdentifierContext for Location leaf.
     *
     * @param schemaHandler Schema context handler.
     * @return InstanceIdentifier of Location leaf.
     */
    private static InstanceIdentifierContext prepareIIDSubsStreamOutput(final EffectiveModelContext modelContext) {
        return InstanceIdentifierContext.ofStack(
            SchemaInferenceStack.ofDataTreePath(modelContext, NOTIFI_QNAME, LOCATION_QNAME));
    }

    /**
     * Holder of all handlers for notifications.
     */
    // FIXME: why do we even need this class?!
    public static final class HandlersHolder {
        private final DOMDataBroker dataBroker;
        private final DOMNotificationService notificationService;
        private final DatabindContextProvider databindContextProvider;

        private HandlersHolder(final DOMDataBroker dataBroker, final DOMNotificationService notificationService,
                final DatabindContextProvider databindContextProvider) {
            this.dataBroker = dataBroker;
            this.notificationService = notificationService;
            this.databindContextProvider = databindContextProvider;
        }

        /**
         * Get {@link DOMDataBroker}.
         *
         * @return the dataBroker
         */
        public DOMDataBroker getDataBroker() {
            return dataBroker;
        }

        /**
         * Get {@link DOMNotificationService}.
         *
         * @return the notificationService
         */
        public DOMNotificationService getNotificationServiceHandler() {
            return notificationService;
        }

        /**
         * Get {@link DatabindContextProvider}.
         *
         * @return the schemaHandler
         */
        public DatabindContextProvider getSchemaHandler() {
            return databindContextProvider;
        }
    }
}

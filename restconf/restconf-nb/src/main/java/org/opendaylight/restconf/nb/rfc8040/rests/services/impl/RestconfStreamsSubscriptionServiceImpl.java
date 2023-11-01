/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStreamsConstants;
import org.opendaylight.yang.gen.v1.subscribe.to.notification.rev161028.Notifi;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfStreamsSubscriptionService}.
 */
@Path("/")
public class RestconfStreamsSubscriptionServiceImpl implements RestconfStreamsSubscriptionService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsSubscriptionServiceImpl.class);
    private static final QName LOCATION_QNAME = QName.create(Notifi.QNAME, "location").intern();
    private static final NodeIdentifier LOCATION_NODEID = NodeIdentifier.create(LOCATION_QNAME);

    private final ListenersBroker listenersBroker;
    private final HandlersHolder handlersHolder;

    /**
     * Initialize holder of handlers with holders as parameters.
     *
     * @param dataBroker {@link DOMDataBroker}
     * @param notificationService {@link DOMNotificationService}
     * @param databindProvider a {@link DatabindProvider}
     * @param listenersBroker a {@link ListenersBroker}
     */
    public RestconfStreamsSubscriptionServiceImpl(final DOMDataBroker dataBroker,
            final DOMNotificationService notificationService, final DatabindProvider databindProvider,
            final ListenersBroker listenersBroker) {
        handlersHolder = new HandlersHolder(dataBroker, notificationService, databindProvider);
        this.listenersBroker = requireNonNull(listenersBroker);
    }

    @Override
    public Response subscribeToStream(final String identifier, final UriInfo uriInfo) {
        final var params = QueryParams.newNotificationQueryParams(uriInfo);

        final URI location;
        if (identifier.contains(RestconfStreamsConstants.DATA_SUBSCRIPTION)) {
            location = listenersBroker.subscribeToDataStream(identifier, uriInfo, params, handlersHolder);
        } else if (identifier.contains(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
            location = listenersBroker.subscribeToYangStream(identifier, uriInfo, params, handlersHolder);
        } else {
            final String msg = "Bad type of notification of sal-remote";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg);
        }

        return Response.ok()
            .location(location)
            .entity(new NormalizedNodePayload(
                Inference.ofDataTreePath(handlersHolder.databindProvider().currentContext().modelContext(),
                    Notifi.QNAME, LOCATION_QNAME),
                ImmutableNodes.leafNode(LOCATION_NODEID, location.toString())))
            .build();
    }

    /**
     * Holder of all handlers for notifications.
     */
    // FIXME: why do we even need this class?!
    public record HandlersHolder(
            @NonNull DOMDataBroker dataBroker,
            @NonNull DOMNotificationService notificationService,
            @NonNull DatabindProvider databindProvider) {

        public HandlersHolder {
            requireNonNull(dataBroker);
            requireNonNull(notificationService);
            requireNonNull(databindProvider);
        }
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.impl;

import java.net.URI;
import java.util.Map;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.restful.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.restful.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.restful.utils.SubscribeToStreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfStreamsSubscriptionService}
 *
 */
public class RestconfStreamsSubscriptionServiceImpl implements RestconfStreamsSubscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsSubscriptionServiceImpl.class);

    private final DOMDataBrokerHandler domDataBrokerHandler;

    public RestconfStreamsSubscriptionServiceImpl(final DOMDataBrokerHandler domDataBrokerHandler) {
        this.domDataBrokerHandler = domDataBrokerHandler;
    }

    @Override
    public Response subscribeToStream(final String identifier, final UriInfo uriInfo) {
        final Map<String, String> mapOfValues = SubscribeToStreamUtil.mapValuesFromUri(identifier);

        final LogicalDatastoreType ds = SubscribeToStreamUtil.parseURIEnum(LogicalDatastoreType.class,
                mapOfValues.get(RestconfStreamsConstants.DATASTORE_PARAM_NAME));
        if (ds == null) {
            final String msg = "Stream name doesn't contains datastore value (pattern /datastore=)";
            LOG.debug(msg);
            throw new RestconfDocumentedException(msg, ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        final DataChangeScope scope = SubscribeToStreamUtil.parseURIEnum(DataChangeScope.class,
                mapOfValues.get(RestconfStreamsConstants.SCOPE_PARAM_NAME));
        if (scope == null) {
            final String msg = "Stream name doesn't contains datastore value (pattern /scope=)";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg, ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        final String streamName = Notificator.createStreamNameFromUri(identifier);

        final ListenerAdapter listener = Notificator.getListenerFor(streamName);
        SubscribeToStreamUtil.registration(ds, scope, listener, this.domDataBrokerHandler.get());

        final int port = SubscribeToStreamUtil.prepareNotificationPort();

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
        final UriBuilder uriToWebSocketServer = uriBuilder.port(port).scheme(RestconfStreamsConstants.SCHEMA_SUBSCIBRE_URI);
        final URI uri = uriToWebSocketServer.replacePath(streamName).build();

        return Response.status(Status.OK).location(uri).build();
    }
}

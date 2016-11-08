/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.impl;

import com.google.common.base.Strings;
import java.net.URI;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.restful.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.restful.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.restful.utils.SubscribeToStreamUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfStreamsSubscriptionService}
 *
 */
public class RestconfStreamsSubscriptionServiceImpl implements RestconfStreamsSubscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsSubscriptionServiceImpl.class);

    private final DOMDataBrokerHandler domDataBrokerHandler;

    private final NotificationServiceHandler notificationServiceHandler;

    private final SchemaContextHandler schemaHandler;

    public RestconfStreamsSubscriptionServiceImpl(final DOMDataBrokerHandler domDataBrokerHandler,
            final NotificationServiceHandler notificationServiceHandler, final SchemaContextHandler schemaHandler) {
        this.domDataBrokerHandler = domDataBrokerHandler;
        this.notificationServiceHandler = notificationServiceHandler;
        this.schemaHandler = schemaHandler;
    }

    @Override
    public NormalizedNodeContext subscribeToStream(final String identifier, final UriInfo uriInfo) {
        boolean startTime_used = false;
        boolean stopTime_used = false;
        Date start = null;
        Date stop = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "start-time":
                    if (!startTime_used) {
                        startTime_used = true;
                        start = parseDateFromQueryParam(entry);
                    } else {
                        throw new RestconfDocumentedException("Start-time parameter can be used only once.");
                    }
                    break;
                case "stop-time":
                    if (!stopTime_used) {
                        stopTime_used = true;
                        stop = parseDateFromQueryParam(entry);
                    } else {
                        throw new RestconfDocumentedException("Stop-time parameter can be used only once.");
                    }
                    break;
                default:
                    throw new RestconfDocumentedException("Bad parameter used with notifications: " + entry.getKey());
            }
        }
        if (!startTime_used && stopTime_used) {
            throw new RestconfDocumentedException("Stop-time parameter has to be used with start-time parameter.");
        }
        URI response = null;
        if (identifier.contains(RestconfStreamsConstants.DATA_SUBSCR)) {
            response = dataSubs(identifier, uriInfo, start, stop);
        } else if (identifier.contains(RestconfStreamsConstants.NOTIFICATION_STREAM)) {
            response = notifStream(identifier, uriInfo, start, stop);
        }

        if (response != null) {
            // prepare node with value of location
            final InstanceIdentifierContext<?> iid = prepareIIDSubsStreamOutput();
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
     * @param identifier
     * @param uriInfo
     * @param start
     * @param stop
     * @return
     */
    private URI notifStream(final String identifier, final UriInfo uriInfo, final Date start, final Date stop) {
        final String streamName = Notificator.createStreamNameFromUri(identifier);
        if (Strings.isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        final List<NotificationListenerAdapter> listeners = Notificator.getNotificationListenerFor(streamName);
        if ((listeners == null) || listeners.isEmpty()) {
            throw new RestconfDocumentedException("Stream was not found.", ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }

        for (final NotificationListenerAdapter listener : listeners) {
            registerToListenNotification(listener);
            listener.setTime(start, stop);
        }

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
        int notificationPort = RestconfStreamsConstants.NOTIFICATION_PORT;
        try {
            final WebSocketServer webSocketServerInstance = WebSocketServer.getInstance();
            notificationPort = webSocketServerInstance.getPort();
        } catch (final NullPointerException e) {
            WebSocketServer.createInstance(RestconfStreamsConstants.NOTIFICATION_PORT);
        }
        final UriBuilder uriToWebsocketServerBuilder = uriBuilder.port(notificationPort).scheme("ws");
        final URI uriToWebsocketServer = uriToWebsocketServerBuilder.replacePath(streamName).build();

        return uriToWebsocketServer;
    }

    private void registerToListenNotification(final NotificationListenerAdapter listener) {

        if (listener.isListening()) {
            return;
        }

        final SchemaPath path = listener.getSchemaPath();
        final ListenerRegistration<DOMNotificationListener> registration =
                this.notificationServiceHandler.get().registerNotificationListener(listener, path);

        listener.setRegistration(registration);
    }

    /**
     * @return
     */
    private InstanceIdentifierContext<?> prepareIIDSubsStreamOutput() {
        final QName qnameBase = QName.create("subscribe:to:notification", "2016-10-28", "notifi");
        final DataSchemaNode location = ((ContainerSchemaNode) this.schemaHandler.get()
                .findModuleByNamespaceAndRevision(qnameBase.getNamespace(), qnameBase.getRevision())
                .getDataChildByName(qnameBase)).getDataChildByName(QName.create(qnameBase, "location"));
        final List<PathArgument> path = new ArrayList<>();
        path.add(NodeIdentifier.create(qnameBase));
        path.add(NodeIdentifier.create(QName.create(qnameBase, "location")));

        return new InstanceIdentifierContext<SchemaNode>(YangInstanceIdentifier.create(path), location, null,
                this.schemaHandler.get());
    }

    /**
     * @param identifier
     * @param uriInfo
     * @param start
     * @param stop
     * @return
     */
    private URI dataSubs(final String identifier, final UriInfo uriInfo, final Date start, final Date stop) {
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
        listener.setTimer(start, stop);
        SubscribeToStreamUtil.registration(ds, scope, listener, this.domDataBrokerHandler.get());

        final int port = SubscribeToStreamUtil.prepareNotificationPort();

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
        final UriBuilder uriToWebSocketServer = uriBuilder.port(port).scheme(RestconfStreamsConstants.SCHEMA_SUBSCIBRE_URI);
        return uriToWebSocketServer.replacePath(streamName).build();
    }

    private Date parseDateFromQueryParam(final Entry<String, List<String>> entry) {
        final DateAndTime event = new DateAndTime(entry.getValue().iterator().next());
        String numOf_ms = "";
        final String value = event.getValue();
        if (value.contains(".")) {
            numOf_ms = numOf_ms + ".";
            final int lastChar = value.contains("Z") ? value.indexOf("Z") : (value.contains("+") ? value.indexOf("+")
                    : (value.contains("-") ? value.indexOf("-") : value.length()));
            for (int i = 0; i < (lastChar - value.indexOf(".") - 1); i++) {
                numOf_ms = numOf_ms + "S";
            }
        }
        String zone = "";
        if (!value.contains("Z")) {
            zone = zone + "XXX";
        }
        final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss" + numOf_ms + zone);

        try {
            return dateFormatter.parse(value.contains("Z") ? value.replace('T', ' ').substring(0, value.indexOf("Z"))
                    : value.replace('T', ' '));
        } catch (final ParseException e) {
            throw new RestconfDocumentedException("Cannot parse of value in date: " + value + e);
        }
    }
}

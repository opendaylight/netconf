/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.http2;

import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERRORS_CONTAINER_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_LIST_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_MESSAGE_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_TAG_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.ERROR_TYPE_QNAME;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.NAMESPACE;
import static org.opendaylight.restconf.nb.rfc8040.Rfc8040.RestconfModule.REVISION;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.ErrorCode;
import org.eclipse.jetty.http2.IStream;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.GoAwayFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.PushPromiseFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.http2.frames.SettingsFrame;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.util.Callback;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.NotificationsHolder;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.RegisteredNotificationWrapper;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NotificationStreamSessionListener extends ServerSessionListener.Adapter implements Stream.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationStreamSessionListener.class);
    private static final String NOTIFICATION_STREAM_SUBSCRIPTION_URI_STR = "/restconf/notification/(.+)/(.+)";
    private static final Pattern NOTIFICATION_STREAM_SUBSCRIPTION_URI_PATTERN = Pattern.compile(
            NOTIFICATION_STREAM_SUBSCRIPTION_URI_STR);

    private static final int MAX_DYNAMIC_TABLE_SIZE = 4096;
    private static final int INITIAL_SESSION_RECV_WINDOW = 1024 * 1024;
    private static final int MAX_CONCURRENT_STREAMS = 128;
    private static final int REQUEST_HEADER_SIZE = 8 * 1024;

    private final Connector connector;
    private final EndPoint endPoint;
    private final NotificationsHolder notificationsHolder;
    private final DOMSchemaService domSchemaService;
    private final DOMMountPointService domMountPointService;

    NotificationStreamSessionListener(final Connector connector, final EndPoint endPoint,
            final NotificationsHolder notificationsHolder, final DOMSchemaService domSchemaService,
            final DOMMountPointService domMountPointService) {
        this.connector = connector;
        this.endPoint = endPoint;
        this.notificationsHolder = notificationsHolder;
        this.domSchemaService = domSchemaService;
        this.domMountPointService = domMountPointService;
    }

    protected HTTP2ServerConnection getConnection() {
        return (HTTP2ServerConnection)endPoint.getConnection();
    }

    @Override
    public Map<Integer, Integer> onPreface(Session session) {
        LOG.debug("onPreface");
        final Map<Integer, Integer> settings = new HashMap<>();
        settings.put(SettingsFrame.HEADER_TABLE_SIZE, MAX_DYNAMIC_TABLE_SIZE);
        settings.put(SettingsFrame.INITIAL_WINDOW_SIZE, INITIAL_SESSION_RECV_WINDOW);
        if (MAX_CONCURRENT_STREAMS >= 0) {
            settings.put(SettingsFrame.MAX_CONCURRENT_STREAMS, MAX_CONCURRENT_STREAMS);
        }
        settings.put(SettingsFrame.MAX_HEADER_LIST_SIZE, REQUEST_HEADER_SIZE);
        return settings;
    }

    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
    @Override
    public Stream.Listener onNewStream(Stream stream, HeadersFrame frame) {
        LOG.debug("onNewStream");
        if (frame.getMetaData().isRequest()) {
            final MetaData.Request request = (MetaData.Request) frame.getMetaData();
            String uriPath = request.getURI().getPath();
            if (uriPath.endsWith("/")) {
                uriPath = uriPath.substring(0, uriPath.length() - 1);
            }
            LOG.debug("isRequest {} {}", request.getMethod(), uriPath);

            final Matcher matcher = NOTIFICATION_STREAM_SUBSCRIPTION_URI_PATTERN.matcher(uriPath);
            if (matcher.matches() && request.getMethod().equals("POST")) {
                LOG.debug("Notification subscription request via http/2 stream");
                processNotificationSubscriptionRequest(matcher.group(2), matcher.group(1), stream,
                        request.getFields().get(HttpHeader.ACCEPT));
            }
        }
        getConnection().onNewStream(connector, (IStream)stream, frame);
        return this;
    }

    private void processNotificationSubscriptionRequest(final String streamSubscriptionId, final String streamName,
            final Stream http2Stream, final String httpAcceptHeader) {
        LOG.debug("streamSubscriptionId is: {}.", streamSubscriptionId);
        Uint32 subscriptionIdNum;
        try {
            subscriptionIdNum = Uint32.valueOf(streamSubscriptionId);
        } catch (final NumberFormatException e) {
            writeHttpErrorResponse(http2Stream, httpAcceptHeader, "Invalid subscription-id in the request URI: "
                    + streamSubscriptionId + ". It must be a uint32 number.");
            return;
        }

        final RegisteredNotificationWrapper notificationSubscription = notificationsHolder.getNotification(
                subscriptionIdNum);
        if (notificationSubscription == null) {
            writeHttpErrorResponse(http2Stream, httpAcceptHeader, "Notification stream subscription with id "
                    + streamSubscriptionId + " does not exist.");
            return;
        }

        final SchemaContext schemaContext;
        String stream;
        if (streamName.contains("yang-ext:mount")) {

            String substring = streamName.substring(streamName.indexOf("topology=") + 9);
            final String topologyId = substring.substring(0, substring.indexOf('/'));
            substring = streamName.substring(streamName.indexOf("node=") + 5);
            final String nodeId = substring.substring(0, substring.indexOf('/'));
            final YangInstanceIdentifier topologyNodeYIID = createTopologyNodeYIID(topologyId, nodeId);
            final Optional<DOMMountPoint> mountPointOpt = this.domMountPointService.getMountPoint(topologyNodeYIID);

            if (mountPointOpt.isPresent()) {
                final DOMMountPoint domMountPoint = mountPointOpt.get();
                schemaContext = domMountPoint.getSchemaContext();
                stream = streamName.split("yang-ext:mount/")[1];
            } else {
                LOG.error("Mount point {} in {} does not exist.", topologyId, nodeId);
                throw new IllegalStateException();
            }
        } else {
            schemaContext = domSchemaService.getGlobalContext();
            stream = streamName;
        }

        final NotificationDefinition streamSubscriptionNotificationDef = notificationSubscription
                .getSubscriptionNotificationListener().getNotificationDefinition();
        final String modulePrefixAndName = SubscribedNotificationsUtil.qNameToModulePrefixAndName(
                streamSubscriptionNotificationDef.getQName(), schemaContext);

        final Matcher prefixedNotificationStreamNameMatcher = SubscribedNotificationsUtil
                .PREFIXED_NOTIFICATION_STREAM_NAME_PATTERN
                .matcher(stream);
        if (!prefixedNotificationStreamNameMatcher.matches()) {
            writeHttpErrorResponse(http2Stream, httpAcceptHeader, "Name of the notification stream in the request URI "
                    + "should be prefixed with the name of the module to which it belongs. The correct form is "
                    + "module:notification. In case of this subscription it should be: " + modulePrefixAndName);
            return;
        }

        if (!modulePrefixAndName.equals(stream)) {
            writeHttpErrorResponse(http2Stream, httpAcceptHeader, "Notification stream subscription with id "
                    + streamSubscriptionId + " does not belong to stream: '" + stream + "'.");
            return;
        }

        notificationSubscription.getSubscriptionNotificationListener().addHttp2Stream(http2Stream);
        notificationSubscription.getSubscriptionNotificationListener().replayNotifications();
    }

    private static YangInstanceIdentifier createTopologyNodeYIID(final String topologyId, final String nodeId) {
        final YangInstanceIdentifier.InstanceIdentifierBuilder builder =
                YangInstanceIdentifier.builder();
        builder.node(NetworkTopology.QNAME).node(Topology.QNAME).nodeWithKey(Topology.QNAME, QName.create(
                Topology.QNAME, "topology-id"), topologyId).node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), nodeId);
        return builder.build();
    }

    private void writeHttpErrorResponse(final Stream http2Stream, final String httpAcceptHeader,
            final String errorMessage) {
        final SchemaContext schemaContext = domSchemaService.getGlobalContext();
        final Optional<Module> ietfRestconfModule = schemaContext.findModule(URI.create(NAMESPACE), REVISION);

        if (!ietfRestconfModule.isPresent()) {
            LOG.warn("Module 'ietf-restconf@2017-01-26' not available.");
            writePlainTextResponse(http2Stream, errorMessage);
            return;
        }

        // grouping errors and container errors have the same QName
        final Optional<? extends GroupingDefinition> errorsGroupingOpt =
                ietfRestconfModule.get().getGroupings().stream()
                        .filter(grouping -> grouping.getQName().equals(ERRORS_CONTAINER_QNAME))
                        .findFirst();

        if (!errorsGroupingOpt.isPresent()) {
            LOG.warn("Grouping 'errors' from module 'ietf-restconf@2017-01-26' not available.");
            writePlainTextResponse(http2Stream, errorMessage);
            return;
        }

        final ContainerSchemaNode errorsContainerSchemaNode = (ContainerSchemaNode) errorsGroupingOpt.get()
                .findDataChildByName(ERRORS_CONTAINER_QNAME).get();

        if (errorsContainerSchemaNode == null) {
            LOG.warn("Container 'errors' from module 'ietf-restconf@2017-01-26' not available.");
            writePlainTextResponse(http2Stream, errorMessage);
            return;
        }

        final ContainerNode errorsContainerNode = createErrorsContainerNodeData(errorMessage);

        final String responseBody;
        if (httpAcceptHeader != null) {
            final String mediaType = httpAcceptHeader.substring(httpAcceptHeader.indexOf('/'));
            if (mediaType.endsWith("xml")) {
                responseBody = SubscribedNotificationsUtil.createXmlResponseBody(errorsContainerNode,
                        errorsContainerSchemaNode.getPath(), schemaContext);
            } else {
                responseBody = SubscribedNotificationsUtil.createJsonResponseBody(errorsContainerNode,
                        errorsContainerSchemaNode.getPath(), schemaContext);
            }
        } else {
            // if no Accept header at all was set in the request, then default to json
            responseBody = SubscribedNotificationsUtil.createJsonResponseBody(errorsContainerNode,
                    errorsContainerSchemaNode.getPath(), schemaContext);
        }

        final DataFrame errorResponseDataFrame = new DataFrame(http2Stream.getId(),
                ByteBuffer.wrap(responseBody.getBytes(StandardCharsets.UTF_8)), true);
        http2Stream.data(errorResponseDataFrame, Callback.NOOP);
    }

    private static void writePlainTextResponse(final Stream http2Stream, final String errorMessage) {
        final DataFrame errorResponseDataFrame = new DataFrame(http2Stream.getId(),
                ByteBuffer.wrap(errorMessage.getBytes(StandardCharsets.UTF_8)), true);
        http2Stream.data(errorResponseDataFrame, Callback.NOOP);
    }

    private static ContainerNode createErrorsContainerNodeData(final String errorMessage) {
        final LeafNode<String> errorTypeLeafNode = Builders.<String>leafBuilder().withNodeIdentifier(
                new NodeIdentifier(ERROR_TYPE_QNAME)).withValue(
                RestconfError.ErrorType.PROTOCOL.getErrorTypeTag()).build();
        final LeafNode<String> errorTagLeafNode = Builders.<String>leafBuilder().withNodeIdentifier(
                new NodeIdentifier(ERROR_TAG_QNAME)).withValue(
                RestconfError.ErrorTag.INVALID_VALUE.getTagValue()).build();
        final LeafNode<String> errorMessageLeafNode = Builders.<String>leafBuilder().withNodeIdentifier(
                new NodeIdentifier(ERROR_MESSAGE_QNAME)).withValue(errorMessage).build();

        final UnkeyedListEntryNode errorListEntryNode = Builders.unkeyedListEntryBuilder().withNodeIdentifier(
                new NodeIdentifier(ERROR_LIST_QNAME)).withChild(errorTypeLeafNode)
                .withChild(errorTagLeafNode).withChild(errorMessageLeafNode).build();
        final UnkeyedListNode errorListNode = Builders.unkeyedListBuilder().withNodeIdentifier(
                new NodeIdentifier(ERROR_LIST_QNAME))
                .withChild(errorListEntryNode).build();

        return Builders.containerBuilder().withNodeIdentifier(
                new NodeIdentifier(ERRORS_CONTAINER_QNAME)).withChild(errorListNode).build();
    }

    @Override
    public boolean onIdleTimeout(Session session) {
        LOG.debug("Ignoring session idle timeout.");
        return false;
    }

    @Override
    public boolean onIdleTimeout(Stream stream, Throwable throwable) {
        LOG.debug("Ignoring stream idle timeout");
        return false;
    }

    @Override
    public void onClose(Session session, GoAwayFrame frame, Callback callback) {
        LOG.debug("onClose");
        ErrorCode error = ErrorCode.from(frame.getError());
        if (error == null) {
            error = ErrorCode.STREAM_CLOSED_ERROR;
        }
        String reason = frame.tryConvertPayload();
        if (reason != null && !reason.isEmpty()) {
            reason = " (" + reason + ")";
        }
        getConnection().onSessionFailure(new EofException("HTTP/2 " + error + reason), callback);
    }

    @Override
    public void onFailure(Session session, Throwable failure, Callback callback) {
        LOG.debug("onFailure");
        getConnection().onSessionFailure(failure, callback);
    }

    @Override
    public void onHeaders(Stream stream, HeadersFrame frame) {
        LOG.debug("onHeaders");
        if (frame.isEndStream()) {
            getConnection().onTrailers((IStream) stream, frame);
        } else {
            close(stream, "invalid_trailers");
        }
    }

    @Override
    public Stream.Listener onPush(Stream stream, PushPromiseFrame frame) {
        LOG.debug("onPush");
        // Servers do not receive pushes.
        close(stream, "push_promise");
        return null;
    }

    private void close(Stream stream, String reason) {
        LOG.debug("close");
        stream.getSession().close(ErrorCode.PROTOCOL_ERROR.code, reason, Callback.NOOP);
    }

    @Override
    public void onData(Stream stream, DataFrame frame, Callback callback) {
        LOG.debug("onData");
        getConnection().onData((IStream)stream, frame, callback);
    }

    @Override
    public void onReset(Stream stream, ResetFrame frame) {
        LOG.debug("onReset");
        ErrorCode error = ErrorCode.from(frame.getError());
        if (error == null) {
            error = ErrorCode.CANCEL_STREAM_ERROR;
        }
        getConnection().onStreamFailure((IStream)stream, new EofException("HTTP/2 " + error), Callback.NOOP);
    }
}

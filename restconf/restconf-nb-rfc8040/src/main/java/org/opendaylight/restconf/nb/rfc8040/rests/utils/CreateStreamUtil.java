/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.SchemaPathCodec;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creation of data-change-event or YANG notification streams.
 */
public final class CreateStreamUtil {

    private static final Logger LOG = LoggerFactory.getLogger(CreateStreamUtil.class);

    private CreateStreamUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Create data-change-event or notification stream with POST operation via RPC.
     *
     * @param payload      Input of RPC - example in JSON (data-change-event stream):
     *                     <pre>
     *                     {@code
     *                         {
     *                             "input": {
     *                                 "path": "/toaster:toaster/toaster:toasterStatus",
     *                                 "sal-remote-augment:datastore": "OPERATIONAL",
     *                                 "sal-remote-augment:scope": "ONE"
     *                             }
     *                         }
     *                     }
     *                     </pre>
     * @param refSchemaCtx Reference to {@link SchemaContext} - {@link SchemaContextRef}.
     * @return {@link DOMRpcResult} - Output of RPC - example in JSON:
     *     <pre>
     *     {@code
     *         {
     *             "output": {
     *                 "stream-name": "toaster:toaster/toaster:toasterStatus/datastore=OPERATIONAL/scope=ONE"
     *             }
     *         }
     *     }
     *     </pre>
     */
    public static DOMRpcResult createDataChangeNotifiStream(final NormalizedNodeContext payload,
            final SchemaContextRef refSchemaCtx) {
        // parsing out of container with settings and path
        final ContainerNode data = (ContainerNode) requireNonNull(payload).getData();
        final QName qname = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final YangInstanceIdentifier path = preparePath(data, qname);

        // building of stream name
        final StringBuilder streamNameBuilder = new StringBuilder(
                prepareDataChangeNotifiStreamName(path, requireNonNull(refSchemaCtx).get(), data));
        final NotificationOutputType outputType = prepareOutputType(data);
        if (outputType.equals(NotificationOutputType.JSON)) {
            streamNameBuilder.append('/').append(outputType.getName());
        }
        final String streamName = streamNameBuilder.toString();

        // registration of the listener
        ListenersBroker.getInstance().registerDataChangeListener(path, streamName, outputType);

        // building of output
        final QName outputQname = QName.create(qname, RestconfStreamsConstants.OUTPUT_CONTAINER_NAME);
        final QName streamNameQname = QName.create(qname, RestconfStreamsConstants.OUTPUT_STREAM_NAME);

        final ContainerNode output = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new NodeIdentifier(outputQname))
                .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();
        return new DefaultDOMRpcResult(output);
    }

    /**
     * Prepare {@link NotificationOutputType}.
     *
     * @param data Container with stream settings (RPC create-stream).
     * @return Parsed {@link NotificationOutputType}.
     */
    private static NotificationOutputType prepareOutputType(final ContainerNode data) {
        NotificationOutputType outputType = parseEnum(
                data, NotificationOutputType.class, RestconfStreamsConstants.OUTPUT_TYPE_PARAM_NAME);
        return outputType == null ? NotificationOutputType.XML : outputType;
    }

    /**
     * Prepare stream name.
     *
     * @param path          Path of element from which data-change-event notifications are going to be generated.
     * @param schemaContext Schema context.
     * @param data          Container with stream settings (RPC create-stream).
     * @return Parsed stream name.
     */
    private static String prepareDataChangeNotifiStreamName(final YangInstanceIdentifier path,
            final SchemaContext schemaContext, final ContainerNode data) {
        LogicalDatastoreType datastoreType = parseEnum(
                data, LogicalDatastoreType.class, RestconfStreamsConstants.DATASTORE_PARAM_NAME);
        datastoreType = datastoreType == null ? RestconfStreamsConstants.DEFAULT_DS : datastoreType;

        DataChangeScope scope = parseEnum(data, DataChangeScope.class, RestconfStreamsConstants.SCOPE_PARAM_NAME);
        scope = scope == null ? RestconfStreamsConstants.DEFAULT_SCOPE : scope;

        return RestconfStreamsConstants.DATA_SUBSCRIPTION
                + "/"
                + ListenersBroker.createStreamNameFromUri(
                ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext)
                        + RestconfStreamsConstants.DS_URI
                        + datastoreType
                        + RestconfStreamsConstants.SCOPE_URI
                        + scope);
    }

    /**
     * Prepare {@link YangInstanceIdentifier} of stream source.
     *
     * @param data          Container with stream settings (RPC create-stream).
     * @param qualifiedName QName of the input RPC context (used only in debugging).
     * @return Parsed {@link YangInstanceIdentifier} of data element from which the data-change-event notifications
     *     are going to be generated.
     */
    private static YangInstanceIdentifier preparePath(final ContainerNode data, final QName qualifiedName) {
        final Optional<DataContainerChild<? extends PathArgument, ?>> path = data.getChild(
                new YangInstanceIdentifier.NodeIdentifier(QName.create(
                        qualifiedName,
                        RestconfStreamsConstants.STREAM_PATH_PARAM_NAME)));
        Object pathValue = null;
        if (path.isPresent()) {
            pathValue = path.get().getValue();
        }
        if (!(pathValue instanceof YangInstanceIdentifier)) {
            LOG.debug("Instance identifier {} was not normalized correctly", qualifiedName);
            throw new RestconfDocumentedException(
                    "Instance identifier was not normalized correctly",
                    ErrorType.APPLICATION,
                    ErrorTag.OPERATION_FAILED);
        }
        return (YangInstanceIdentifier) pathValue;
    }

    /**
     * Parsing out of enumeration from RPC create-stream body.
     *
     * @param data      Container with stream settings (RPC create-stream).
     * @param clazz     Enum type to be parsed out from input container.
     * @param paramName Local name of the enum element.
     * @return Parsed enumeration.
     */
    private static <T> T parseEnum(final ContainerNode data, final Class<T> clazz, final String paramName) {
        final Optional<DataContainerChild<? extends PathArgument, ?>> optAugNode = data.getChild(
                RestconfStreamsConstants.SAL_REMOTE_AUG_IDENTIFIER);
        if (!optAugNode.isPresent()) {
            return null;
        }
        final DataContainerChild<? extends PathArgument, ?> augNode = optAugNode.get();
        if (!(augNode instanceof AugmentationNode)) {
            return null;
        }
        final Optional<DataContainerChild<? extends PathArgument, ?>> enumNode = ((AugmentationNode) augNode).getChild(
                new NodeIdentifier(QName.create(RestconfStreamsConstants.SAL_REMOTE_AUGMENT, paramName)));
        if (!enumNode.isPresent()) {
            return null;
        }
        final Object value = enumNode.get().getValue();
        if (!(value instanceof String)) {
            return null;
        }

        return ResolveEnumUtil.resolveEnum(clazz, (String) value);
    }

    /**
     * Create YANG notification stream using notification definition in YANG schema.
     *
     * @param notificationDefinition YANG notification definition.
     * @param refSchemaCtx           Reference to {@link SchemaContext} - {@link SchemaContextRef}.
     * @param outputType             Output type (XML or JSON).
     * @return {@link NotificationListenerAdapter}
     */
    public static NotificationListenerAdapter createYangNotifiStream(
            final NotificationDefinition notificationDefinition, final SchemaContextRef refSchemaCtx,
            final NotificationOutputType outputType) {
        final String streamName = parseNotificationStreamName(requireNonNull(notificationDefinition),
                requireNonNull(refSchemaCtx), requireNonNull(outputType.getName()));
        return ListenersBroker.getInstance().registerNotificationListener(notificationDefinition.getPath(),
                streamName, outputType);
    }

    private static String parseNotificationStreamName(final NotificationDefinition notificationDefinition,
            final SchemaContextRef refSchemaCtx, final String outputType) {
        final String serializedSchemaPath = serializeAndNormalizeSchemaPath(
                notificationDefinition.getPath(), refSchemaCtx.get());
        final StringBuilder streamNameBuilder = new StringBuilder();
        streamNameBuilder.append(RestconfStreamsConstants.NOTIFICATION_STREAM);
        if (!serializedSchemaPath.isEmpty()) {
            streamNameBuilder.append(RestconfConstants.SLASH).append(serializedSchemaPath);
        }

        if (outputType.equals(NotificationOutputType.JSON.getName())) {
            streamNameBuilder.append(RestconfConstants.SLASH).append(NotificationOutputType.JSON.getName());
        }
        return streamNameBuilder.toString();
    }

    /**
     * Serialization of the input schema path and removal of leading dot and slash characters from the input path
     * (these characters are used for differentiation between absolute and relative schema paths, but in this case
     * only absolute schema paths have a sense).
     *
     * @param schemaPath    Schema path used as input for serialization.
     * @param schemaContext Schema context used for serialization.
     * @return Normalized path.
     */
    static String serializeAndNormalizeSchemaPath(final SchemaPath schemaPath, final SchemaContext schemaContext) {
        String serializePath = SchemaPathCodec.serialize(schemaPath, schemaContext);
        if (serializePath.startsWith(String.valueOf(RestconfConstants.DOT))) {
            serializePath = serializePath.substring(1);
        }
        if (serializePath.startsWith(String.valueOf(RestconfConstants.SLASH))) {
            serializePath = serializePath.substring(1);
        }
        return serializePath;
    }
}
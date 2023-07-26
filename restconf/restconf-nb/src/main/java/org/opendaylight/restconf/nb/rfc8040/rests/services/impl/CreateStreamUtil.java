/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.DeviceNotificationListenerAdaptor;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.CreateDataChangeEventSubscriptionInput1.Scope;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * Utility class for creation of data-change-event or YANG notification streams.
 */
final class CreateStreamUtil {
    private static final QNameModule SAL_REMOTE_AUGMENT = NotificationOutputTypeGrouping.QNAME.getModule();

    private static final QNameModule DEVICE_NOTIFICATION_MODULE = SubscribeDeviceNotificationInput.QNAME.getModule();
    private static final QName DATASTORE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, RestconfStreamsConstants.DATASTORE_PARAM_NAME).intern();
    private static final QName SCOPE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, RestconfStreamsConstants.SCOPE_PARAM_NAME).intern();
    private static final QName OUTPUT_TYPE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, "notification-output-type").intern();
    private static final QName DEVICE_NOTIFICATION_PATH_QNAME =
        QName.create(DEVICE_NOTIFICATION_MODULE, "path").intern();
    private static final QName DEVICE_NOTIFICATION_STREAM_PATH =
        QName.create(DEVICE_NOTIFICATION_PATH_QNAME, "stream-path").intern();
    private static final NodeIdentifier DATASTORE_NODEID = NodeIdentifier.create(DATASTORE_QNAME);
    private static final NodeIdentifier SCOPE_NODEID = NodeIdentifier.create(SCOPE_QNAME);
    private static final NodeIdentifier OUTPUT_TYPE_NODEID = NodeIdentifier.create(OUTPUT_TYPE_QNAME);
    private static final NodeIdentifier DEVICE_NOTIFICATION_PATH_NODEID =
        NodeIdentifier.create(DEVICE_NOTIFICATION_PATH_QNAME);
    private static final NodeIdentifier SAL_REMOTE_OUTPUT_NODEID =
        NodeIdentifier.create(CreateDataChangeEventSubscriptionOutput.QNAME);
    private static final NodeIdentifier PATH_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionInput.QNAME, "path").intern());
    private static final NodeIdentifier STREAM_NAME_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionOutput.QNAME, "stream-name").intern());

    private CreateStreamUtil() {
        // Hidden on purpose
    }

    /**
     * Create data-change-event stream with POST operation via RPC.
     *
     * @param input Input of RPC - example in JSON (data-change-event stream):
     *              <pre>
     *              {@code
     *                  {
     *                      "input": {
     *                          "path": "/toaster:toaster/toaster:toasterStatus",
     *                          "sal-remote-augment:datastore": "OPERATIONAL",
     *                          "sal-remote-augment:scope": "ONE"
     *                      }
     *                  }
     *              }
     *              </pre>
     * @param refSchemaCtx Reference to {@link EffectiveModelContext}.
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
    // FIXME: this really should be a normal RPC implementation
    static DOMRpcResult createDataChangeNotifiStream(final ContainerNode input,
            final EffectiveModelContext refSchemaCtx) {
        // parsing out of container with settings and path
        final YangInstanceIdentifier path = preparePath(input);

        // building of stream name
        final StringBuilder streamNameBuilder = new StringBuilder(
                prepareDataChangeNotifiStreamName(path, requireNonNull(refSchemaCtx), input));
        final NotificationOutputType outputType = prepareOutputType(input);
        if (outputType.equals(NotificationOutputType.JSON)) {
            streamNameBuilder.append('/').append(outputType.getName());
        }
        final String streamName = streamNameBuilder.toString();

        // registration of the listener
        ListenersBroker.getInstance().registerDataChangeListener(path, streamName, outputType);

        // building of output
        return new DefaultDOMRpcResult(Builders.containerBuilder()
            .withNodeIdentifier(SAL_REMOTE_OUTPUT_NODEID)
            .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, streamName))
            .build());
    }

    /**
     * Create device notification stream.
     *
     * @param baseUrl base Url
     * @param input RPC input
     * @param streamUtil stream utility
     * @param mountPointService dom mount point service
     * @return {@link DOMRpcResult} - Output of RPC - example in JSON
     */
    static DOMRpcResult createDeviceNotificationListener(final String baseUrl, final ContainerNode input,
            final SubscribeToStreamUtil streamUtil, final DOMMountPointService mountPointService) {
        // parsing out of container with settings and path
        // FIXME: ugly cast
        final YangInstanceIdentifier path =
            (YangInstanceIdentifier) input.findChildByArg(DEVICE_NOTIFICATION_PATH_NODEID)
                .map(DataContainerChild::body)
                .orElseThrow(() -> new RestconfDocumentedException("No path specified", ErrorType.APPLICATION,
                    ErrorTag.DATA_MISSING));

        if (!(path.getLastPathArgument() instanceof NodeIdentifierWithPredicates listId)) {
            throw new RestconfDocumentedException("Path does not refer to a list item", ErrorType.APPLICATION,
                ErrorTag.INVALID_VALUE);
        }
        if (listId.size() != 1) {
            throw new RestconfDocumentedException("Target list uses multiple keys", ErrorType.APPLICATION,
                ErrorTag.INVALID_VALUE);
        }
        final String deviceName = listId.values().iterator().next().toString();

        final DOMMountPoint mountPoint = mountPointService.getMountPoint(path)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point not available", ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED));

        final DOMNotificationService mountNotifService = mountPoint.getService(DOMNotificationService.class)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point does not support notifications",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED));

        final EffectiveModelContext mountModelContext = mountPoint.getService(DOMSchemaService.class)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point schema not available",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED))
            .getGlobalContext();
        final Set<Absolute> notificationPaths = mountModelContext.getModuleStatements().values().stream()
            .flatMap(module -> module.streamEffectiveSubstatements(NotificationEffectiveStatement.class))
            .map(notification -> Absolute.of(notification.argument()))
            .collect(Collectors.toUnmodifiableSet());
        if (notificationPaths.isEmpty()) {
            throw new RestconfDocumentedException("Device does not support notification", ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED);
        }

        final DeviceNotificationListenerAdaptor notificationListenerAdapter = ListenersBroker.getInstance()
            .registerDeviceNotificationListener(deviceName, prepareOutputType(input), mountModelContext,
                mountPointService, mountPoint.getIdentifier());
        notificationListenerAdapter.listen(mountNotifService, notificationPaths);

        // building of output
        return new DefaultDOMRpcResult(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(SubscribeDeviceNotificationOutput.QNAME))
            .withChild(ImmutableNodes.leafNode(DEVICE_NOTIFICATION_STREAM_PATH, baseUrl + deviceName
                + "?" + RestconfStreamsConstants.NOTIFICATION_TYPE + "=" + RestconfStreamsConstants.DEVICE))
            .build());
    }

    /**
     * Prepare {@link NotificationOutputType}.
     *
     * @param data Container with stream settings (RPC create-stream).
     * @return Parsed {@link NotificationOutputType}.
     */
    private static NotificationOutputType prepareOutputType(final ContainerNode data) {
        final String outputName = extractStringLeaf(data, OUTPUT_TYPE_NODEID);
        return outputName != null ? NotificationOutputType.valueOf(outputName) : NotificationOutputType.XML;
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
            final EffectiveModelContext schemaContext, final ContainerNode data) {
        final String datastoreName = extractStringLeaf(data, DATASTORE_NODEID);
        final LogicalDatastoreType datastoreType = datastoreName != null ? LogicalDatastoreType.valueOf(datastoreName)
            : LogicalDatastoreType.CONFIGURATION;

        final String scopeName = extractStringLeaf(data, SCOPE_NODEID);
        // FIXME: this is not really used
        final Scope scope = scopeName != null ? Scope.ofName(scopeName) : Scope.BASE;

        return RestconfStreamsConstants.DATA_SUBSCRIPTION
            + "/" + ListenersBroker.createStreamNameFromUri(IdentifierCodec.serialize(path, schemaContext)
                + "/" + RestconfStreamsConstants.DATASTORE_PARAM_NAME + "=" + datastoreType
                + "/" + RestconfStreamsConstants.SCOPE_PARAM_NAME + "=" + scope);
    }

    /**
     * Prepare {@link YangInstanceIdentifier} of stream source.
     *
     * @param data Container with stream settings (RPC create-stream).
     * @return Parsed {@link YangInstanceIdentifier} of data element from which the data-change-event notifications
     *         are going to be generated.
     */
    private static YangInstanceIdentifier preparePath(final ContainerNode data) {
        final var pathLeaf = data.childByArg(PATH_NODEID);
        if (pathLeaf != null && pathLeaf.body() instanceof YangInstanceIdentifier pathValue) {
            return pathValue;
        }

        throw new RestconfDocumentedException("Instance identifier was not normalized correctly",
            ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
    }

    private static @Nullable String extractStringLeaf(final ContainerNode data, final NodeIdentifier childName) {
        return data.childByArg(childName) instanceof LeafNode<?> leafNode && leafNode.body() instanceof String str
            ? str : null;
    }
}

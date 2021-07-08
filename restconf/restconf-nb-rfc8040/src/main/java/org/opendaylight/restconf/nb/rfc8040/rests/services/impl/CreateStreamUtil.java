/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creation of data-change-event or YANG notification streams.
 */
final class CreateStreamUtil {
    private static final Logger LOG = LoggerFactory.getLogger(CreateStreamUtil.class);
    private static final QNameModule SAL_REMOTE_AUGMENT = QNameModule.create(
        XMLNamespace.of("urn:sal:restconf:event:subscription"), Revision.of("2014-07-08"));
    private static final QName DATASTORE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, RestconfStreamsConstants.DATASTORE_PARAM_NAME).intern();
    private static final QName SCOPE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, RestconfStreamsConstants.SCOPE_PARAM_NAME).intern();
    private static final QName OUTPUT_TYPE_QNAME =
        QName.create(SAL_REMOTE_AUGMENT, RestconfStreamsConstants.OUTPUT_TYPE_PARAM_NAME).intern();
    private static final NodeIdentifier DATASTORE_NODEID = NodeIdentifier.create(DATASTORE_QNAME);
    private static final NodeIdentifier SCOPE_NODEID = NodeIdentifier.create(SCOPE_QNAME);
    private static final NodeIdentifier OUTPUT_TYPE_NODEID = NodeIdentifier.create(OUTPUT_TYPE_QNAME);

    private static final AugmentationIdentifier SAL_REMOTE_AUG_IDENTIFIER = new AugmentationIdentifier(
        ImmutableSet.of(SCOPE_QNAME, DATASTORE_QNAME, OUTPUT_TYPE_QNAME));

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
    static DOMRpcResult createDataChangeNotifiStream(final NormalizedNodeContext payload,
            final EffectiveModelContext refSchemaCtx) {
        // parsing out of container with settings and path
        final ContainerNode data = (ContainerNode) requireNonNull(payload).getData();
        final QName qname = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final YangInstanceIdentifier path = preparePath(data, qname);

        // building of stream name
        final StringBuilder streamNameBuilder = new StringBuilder(
                prepareDataChangeNotifiStreamName(path, requireNonNull(refSchemaCtx), data));
        final NotificationOutputType outputType = prepareOutputType(data);
        if (outputType.equals(NotificationOutputType.JSON)) {
            streamNameBuilder.append('/').append(outputType.getName());
        }
        final String streamName = streamNameBuilder.toString();

        // registration of the listener
        ListenersBroker.getInstance().registerDataChangeListener(path, streamName, outputType);

        // building of output
        final QName outputQname = QName.create(qname, "output");
        final QName streamNameQname = QName.create(qname, "stream-name");

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
        final DataChangeScope scope = scopeName != null ? DataChangeScope.valueOf(scopeName) : DataChangeScope.BASE;

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
        final Optional<DataContainerChild> path = data.findChildByArg(
                new NodeIdentifier(QName.create(qualifiedName, RestconfStreamsConstants.STREAM_PATH_PARAM_NAME)));
        Object pathValue = null;
        if (path.isPresent()) {
            pathValue = path.get().body();
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

    private static @Nullable String extractStringLeaf(final ContainerNode data, final NodeIdentifier childName) {
        final DataContainerChild augNode = data.childByArg(SAL_REMOTE_AUG_IDENTIFIER);
        if (augNode instanceof AugmentationNode) {
            final DataContainerChild enumNode = ((AugmentationNode) augNode).childByArg(childName);
            if (enumNode instanceof LeafNode) {
                final Object value = enumNode.body();
                if (value instanceof String) {
                    return (String) value;
                }
            }
        }
        return null;
    }

    /**
     * Create YANG notification stream using notification definition in YANG schema.
     *
     * @param notificationDefinition YANG notification definition.
     * @param refSchemaCtx           Reference to {@link EffectiveModelContext}
     * @param outputType             Output type (XML or JSON).
     * @return {@link NotificationListenerAdapter}
     */
    static NotificationListenerAdapter createYangNotifiStream(final NotificationDefinition notificationDefinition,
            final EffectiveModelContext refSchemaCtx, final NotificationOutputType outputType) {
        final String streamName = parseNotificationStreamName(requireNonNull(notificationDefinition),
                requireNonNull(refSchemaCtx), requireNonNull(outputType.getName()));
        final Optional<NotificationListenerAdapter> listenerForStreamName = ListenersBroker.getInstance()
                .getNotificationListenerFor(streamName);
        return listenerForStreamName.orElseGet(() -> ListenersBroker.getInstance().registerNotificationListener(
                Absolute.of(ImmutableList.copyOf(notificationDefinition.getPath().getPathFromRoot())), streamName,
                outputType));
    }

    private static String parseNotificationStreamName(final NotificationDefinition notificationDefinition,
            final EffectiveModelContext refSchemaCtx, final String outputType) {
        final QName notificationDefinitionQName = notificationDefinition.getQName();
        final Module module = refSchemaCtx.findModule(
                notificationDefinitionQName.getModule().getNamespace(),
                notificationDefinitionQName.getModule().getRevision()).orElse(null);
        requireNonNull(module, String.format("Module for namespace %s does not exist.",
                notificationDefinitionQName.getModule().getNamespace()));

        final StringBuilder streamNameBuilder = new StringBuilder();
        streamNameBuilder.append(RestconfStreamsConstants.NOTIFICATION_STREAM)
                .append('/')
                .append(module.getName())
                .append(':')
                .append(notificationDefinitionQName.getLocalName());
        if (outputType.equals(NotificationOutputType.JSON.getName())) {
            streamNameBuilder.append('/').append(NotificationOutputType.JSON.getName());
        }
        return streamNameBuilder.toString();
    }
}
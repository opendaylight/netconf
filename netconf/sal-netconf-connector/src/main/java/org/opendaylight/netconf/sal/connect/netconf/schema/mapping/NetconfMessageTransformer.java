/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.IETF_NETCONF_NOTIFICATIONS;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_URI;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.MissingNameSpaceException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class NetconfMessageTransformer implements MessageTransformer<NetconfMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageTransformer.class);

    private static final ImmutableSet<URI> BASE_OR_NOTIFICATION_NS = ImmutableSet.of(
        NETCONF_URI,
        IETF_NETCONF_NOTIFICATIONS.getNamespace(),
        CREATE_SUBSCRIPTION_RPC_QNAME.getNamespace());

    private final SchemaContext schemaContext;
    private final BaseSchema baseSchema;
    private final MessageCounter counter;
    private final ImmutableMap<QName, RpcDefinition> mappedRpcs;
    private final Multimap<QName, NotificationDefinition> mappedNotifications;
    private final boolean strictParsing;
    private final Set<ActionDefinition> actions;

    public NetconfMessageTransformer(final SchemaContext schemaContext, final boolean strictParsing) {
        this(schemaContext, strictParsing, BaseSchema.BASE_NETCONF_CTX);
    }

    public NetconfMessageTransformer(final SchemaContext schemaContext, final boolean strictParsing,
                                     final BaseSchema baseSchema) {
        this.counter = new MessageCounter();
        this.schemaContext = schemaContext;
        this.mappedRpcs = Maps.uniqueIndex(schemaContext.getOperations(), SchemaNode::getQName);
        this.actions = getActions();
        this.mappedNotifications = Multimaps.index(schemaContext.getNotifications(),
            node -> node.getQName().withoutRevision());
        this.baseSchema = baseSchema;
        this.strictParsing = strictParsing;
    }

    @VisibleForTesting
    Set<ActionDefinition> getActions() {
        final Builder<ActionDefinition> builder = ImmutableSet.builder();
        for (DataSchemaNode dataSchemaNode : schemaContext.getChildNodes()) {
            if (dataSchemaNode instanceof ActionNodeContainer) {
                findAction(dataSchemaNode, builder);
            }
        }
        return builder.build();
    }

    private void findAction(final DataSchemaNode dataSchemaNode, final Builder<ActionDefinition> builder) {
        if (dataSchemaNode instanceof ActionNodeContainer) {
            final ActionNodeContainer containerSchemaNode = (ActionNodeContainer) dataSchemaNode;
            for (ActionDefinition actionDefinition : containerSchemaNode.getActions()) {
                builder.add(actionDefinition);
            }
        }
        if (dataSchemaNode instanceof DataNodeContainer) {
            for (DataSchemaNode innerDataSchemaNode : ((DataNodeContainer) dataSchemaNode).getChildNodes()) {
                findAction(innerDataSchemaNode, builder);
            }
        }
    }

    @Override
    public synchronized DOMNotification toNotification(final NetconfMessage message) {
        final Map.Entry<Instant, XmlElement> stripped = NetconfMessageTransformUtil.stripNotification(message);
        final QName notificationNoRev;
        try {
            notificationNoRev = QName.create(
                    stripped.getValue().getNamespace(), stripped.getValue().getName()).withoutRevision();
        } catch (final MissingNameSpaceException e) {
            throw new IllegalArgumentException(
                    "Unable to parse notification " + message + ", cannot find namespace", e);
        }
        final Collection<NotificationDefinition> notificationDefinitions = mappedNotifications.get(notificationNoRev);
        Preconditions.checkArgument(notificationDefinitions.size() > 0,
                "Unable to parse notification %s, unknown notification. Available notifications: %s",
                notificationDefinitions, mappedNotifications.keySet());

        final NotificationDefinition mostRecentNotification = getMostRecentNotification(notificationDefinitions);

        final ContainerSchemaNode notificationAsContainerSchemaNode =
                NetconfMessageTransformUtil.createSchemaForNotification(mostRecentNotification);

        final Element element = stripped.getValue().getDomElement();
        final ContainerNode content;
        try {
            final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
            final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
            final XmlParserStream xmlParser = XmlParserStream.create(writer, schemaContext,
                    notificationAsContainerSchemaNode, strictParsing);
            xmlParser.traverse(new DOMSource(element));
            content = (ContainerNode) resultHolder.getResult();
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException
                | UnsupportedOperationException e) {
            throw new IllegalArgumentException(String.format("Failed to parse notification %s", element), e);
        }
        return new NetconfDeviceNotification(content, stripped.getKey());
    }

    private static NotificationDefinition getMostRecentNotification(
            final Collection<NotificationDefinition> notificationDefinitions) {
        return Collections.max(notificationDefinitions, (o1, o2) ->
            Revision.compare(o1.getQName().getRevision(), o2.getQName().getRevision()));
    }

    @Override
    public NetconfMessage toRpcRequest(final SchemaPath rpc, final NormalizedNode<?, ?> payload) {
        // In case no input for rpc is defined, we can simply construct the payload here
        final QName rpcQName = rpc.getLastComponent();

        // Determine whether a base netconf operation is being invoked
        // and also check if the device exposed model for base netconf.
        // If no, use pre built base netconf operations model
        final boolean needToUseBaseCtx = mappedRpcs.get(rpcQName) == null && isBaseOrNotificationRpc(rpcQName);
        final ImmutableMap<QName, RpcDefinition> currentMappedRpcs;
        if (needToUseBaseCtx) {
            currentMappedRpcs = baseSchema.getMappedRpcs();
        } else {
            currentMappedRpcs = mappedRpcs;
        }

        final RpcDefinition mappedRpc = Preconditions.checkNotNull(currentMappedRpcs.get(rpcQName),
                "Unknown rpc %s, available rpcs: %s", rpcQName, currentMappedRpcs.keySet());
        if (mappedRpc.getInput().getChildNodes().isEmpty()) {
            return new NetconfMessage(NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpcQName, counter)
                .getNode().getOwnerDocument());
        }

        Preconditions.checkNotNull(payload, "Transforming an rpc with input: %s, payload cannot be null", rpcQName);

        Preconditions.checkArgument(payload instanceof ContainerNode,
                "Transforming an rpc with input: %s, payload has to be a container, but was: %s", rpcQName, payload);
        // Set the path to the input of rpc for the node stream writer
        final SchemaPath rpcInput = rpc.createChild(YangConstants.operationInputQName(rpcQName.getModule()));
        final DOMResult result = NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpcQName, counter);

        try {
            // If the schema context for netconf device does not contain model for base netconf operations,
            // use default pre build context with just the base model
            // This way operations like lock/unlock are supported even if the source for base model was not provided
            final SchemaContext ctx = needToUseBaseCtx ? baseSchema.getSchemaContext() : schemaContext;
            NetconfMessageTransformUtil.writeNormalizedRpc((ContainerNode) payload, result, rpcInput, ctx);
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize " + rpcInput, e);
        }

        final Document node = result.getNode().getOwnerDocument();

        return new NetconfMessage(node);
    }

    @Override
    public NetconfMessage toActionRequest(SchemaPath action, final DOMDataTreeIdentifier domDataTreeIdentifier,
            final NormalizedNode<?, ?> payload) {
        ActionDefinition actionDefinition = null;
        SchemaPath schemaPath = action;
        for (ActionDefinition actionDef : actions) {
            if (actionDef.getPath().getLastComponent().equals(action.getLastComponent())) {
                actionDefinition = actionDef;
                schemaPath = actionDef.getPath();
            }
        }
        Preconditions.checkNotNull(actionDefinition, "Action does not exist: %s", action.getLastComponent());

        if (actionDefinition.getInput().getChildNodes().isEmpty()) {
            return new NetconfMessage(NetconfMessageTransformUtil.prepareDomResultForActionRequest(
                    DataSchemaContextTree.from(schemaContext), domDataTreeIdentifier, action, counter,
                    actionDefinition.getQName().getLocalName())
                    .getNode().getOwnerDocument());
        }

        Preconditions.checkNotNull(payload, "Transforming an action with input: %s, payload cannot be null",
                action.getLastComponent());
        Preconditions.checkArgument(payload instanceof ContainerNode,
                "Transforming an rpc with input: %s, payload has to be a container, but was: %s",
                action.getLastComponent(), payload);
        // Set the path to the input of rpc for the node stream writer
        action = action.createChild(QName.create(action.getLastComponent(), "input").intern());
        final DOMResult result = NetconfMessageTransformUtil.prepareDomResultForActionRequest(
                DataSchemaContextTree.from(schemaContext), domDataTreeIdentifier, action, counter,
                actionDefinition.getQName().getLocalName());

        try {
            NetconfMessageTransformUtil.writeNormalizedRpc((ContainerNode) payload, result,
                    schemaPath.createChild(QName.create(action.getLastComponent(), "input").intern()), schemaContext);
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize " + action, e);
        }

        final Document node = result.getNode().getOwnerDocument();

        return new NetconfMessage(node);
    }

    private static boolean isBaseOrNotificationRpc(final QName rpc) {
        return BASE_OR_NOTIFICATION_NS.contains(rpc.getNamespace());
    }

    @Override
    public synchronized DOMRpcResult toRpcResult(final NetconfMessage message, final SchemaPath rpc) {
        final NormalizedNode<?, ?> normalizedNode;
        final QName rpcQName = rpc.getLastComponent();
        if (NetconfMessageTransformUtil.isDataRetrievalOperation(rpcQName)) {
            normalizedNode = Builders.containerBuilder()
                    .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_NODEID)
                    .withChild(Builders.anyXmlBuilder()
                        .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_NODEID)
                        .withValue(new DOMSource(NetconfMessageTransformUtil.getDataSubtree(message.getDocument())))
                        .build())
                    .build();
        } else {
            // Determine whether a base netconf operation is being invoked
            // and also check if the device exposed model for base netconf.
            // If no, use pre built base netconf operations model
            final ImmutableMap<QName, RpcDefinition> currentMappedRpcs;
            if (mappedRpcs.get(rpcQName) == null && isBaseOrNotificationRpc(rpcQName)) {
                currentMappedRpcs = baseSchema.getMappedRpcs();
            } else {
                currentMappedRpcs = mappedRpcs;
            }

            final RpcDefinition rpcDefinition = currentMappedRpcs.get(rpcQName);
            Preconditions.checkArgument(rpcDefinition != null,
                    "Unable to parse response of %s, the rpc is unknown", rpcQName);

            // In case no input for rpc is defined, we can simply construct the payload here
            normalizedNode = parseResult(message, rpcDefinition);
        }
        return new DefaultDOMRpcResult(normalizedNode);
    }

    @Override
    public DOMActionResult toActionResult(final SchemaPath action, final NetconfMessage message) {
        ActionDefinition actionDefinition = null;
        for (ActionDefinition actionDef : actions) {
            if (actionDef.getPath().getLastComponent().equals(action.getLastComponent())) {
                actionDefinition = actionDef;
            }
        }
        Preconditions.checkNotNull(actionDefinition, "Action does not exist: %s", action);
        final ContainerNode normalizedNode = (ContainerNode) parseResult(message, actionDefinition);

        if (normalizedNode == null) {
            return new SimpleDOMActionResult(Collections.<RpcError>emptyList());
        } else {
            return new SimpleDOMActionResult(normalizedNode, Collections.<RpcError>emptyList());
        }
    }

    private NormalizedNode<?, ?> parseResult(final NetconfMessage message,
            final OperationDefinition operationDefinition) {
        if (operationDefinition.getOutput().getChildNodes().isEmpty()) {
            Preconditions.checkArgument(XmlElement.fromDomDocument(
                message.getDocument()).getOnlyChildElementWithSameNamespaceOptionally("ok").isPresent(),
                "Unexpected content in response of rpc: %s, %s", operationDefinition.getQName(), message);
            return null;
        } else {
            final Element element = message.getDocument().getDocumentElement();
            try {
                final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
                final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
                final XmlParserStream xmlParser = XmlParserStream.create(writer, schemaContext,
                        operationDefinition.getOutput(), strictParsing);
                xmlParser.traverse(new DOMSource(element));
                return resultHolder.getResult();
            } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
                throw new IllegalArgumentException(String.format("Failed to parse RPC response %s", element), e);
            }
        }
    }

    static class NetconfDeviceNotification implements DOMNotification, DOMEvent {
        private final ContainerNode content;
        private final SchemaPath schemaPath;
        private final Instant eventTime;

        NetconfDeviceNotification(final ContainerNode content, final Instant eventTime) {
            this.content = content;
            this.eventTime = eventTime;
            this.schemaPath = toPath(content.getNodeType());
        }

        @Override
        public SchemaPath getType() {
            return schemaPath;
        }

        @Override
        public ContainerNode getBody() {
            return content;
        }

        @Override
        public Instant getEventInstant() {
            return eventTime;
        }
    }
}

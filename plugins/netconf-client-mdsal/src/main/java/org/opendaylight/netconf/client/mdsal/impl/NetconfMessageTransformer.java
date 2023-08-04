/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.IETF_NETCONF_NOTIFICATIONS;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_URI;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
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
import org.opendaylight.netconf.client.mdsal.api.ActionTransformer;
import org.opendaylight.netconf.client.mdsal.api.NotificationTransformer;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class NetconfMessageTransformer
        implements ActionTransformer, NotificationTransformer, RpcTransformer<ContainerNode, DOMRpcResult> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageTransformer.class);

    private static final ImmutableSet<XMLNamespace> BASE_OR_NOTIFICATION_NS = ImmutableSet.of(
        NETCONF_URI,
        IETF_NETCONF_NOTIFICATIONS.getNamespace(),
        CREATE_SUBSCRIPTION_RPC_QNAME.getNamespace());

    private final MountPointContext mountContext;
    private final DataSchemaContextTree contextTree;
    private final BaseSchema baseSchema;
    private final MessageCounter counter;
    private final ImmutableMap<QName, ? extends RpcDefinition> mappedRpcs;
    private final Multimap<QName, ? extends NotificationDefinition> mappedNotifications;
    private final boolean strictParsing;
    private final ImmutableMap<Absolute, ActionDefinition> actions;

    public NetconfMessageTransformer(final MountPointContext mountContext, final boolean strictParsing,
                                     final BaseSchema baseSchema) {
        counter = new MessageCounter();
        this.mountContext = requireNonNull(mountContext);

        final EffectiveModelContext schemaContext = mountContext.getEffectiveModelContext();
        contextTree = DataSchemaContextTree.from(schemaContext);

        mappedRpcs = Maps.uniqueIndex(schemaContext.getOperations(), SchemaNode::getQName);
        actions = getActions(schemaContext);

        // RFC6020 normal notifications
        mappedNotifications = Multimaps.index(schemaContext.getNotifications(),
            node -> node.getQName().withoutRevision());
        this.baseSchema = baseSchema;
        this.strictParsing = strictParsing;
    }

    @VisibleForTesting
    static ImmutableMap<Absolute, ActionDefinition> getActions(final EffectiveModelContext schemaContext) {
        final var builder = ImmutableMap.<Absolute, ActionDefinition>builder();
        findAction(schemaContext, new ArrayDeque<>(), builder);
        return builder.build();
    }

    private static void findAction(final DataSchemaNode dataSchemaNode, final Deque<QName> path,
            final ImmutableMap.Builder<Absolute, ActionDefinition> builder) {
        if (dataSchemaNode instanceof ActionNodeContainer) {
            for (ActionDefinition actionDefinition : ((ActionNodeContainer) dataSchemaNode).getActions()) {
                path.addLast(actionDefinition.getQName());
                builder.put(Absolute.of(path), actionDefinition);
                path.removeLast();
            }
        }
        if (dataSchemaNode instanceof DataNodeContainer) {
            for (DataSchemaNode innerDataSchemaNode : ((DataNodeContainer) dataSchemaNode).getChildNodes()) {
                path.addLast(innerDataSchemaNode.getQName());
                findAction(innerDataSchemaNode, path, builder);
                path.removeLast();
            }
        } else if (dataSchemaNode instanceof ChoiceSchemaNode) {
            for (CaseSchemaNode caze : ((ChoiceSchemaNode) dataSchemaNode).getCases()) {
                path.addLast(caze.getQName());
                findAction(caze, path, builder);
                path.removeLast();
            }
        }
    }

    @Override
    public synchronized DOMNotification toNotification(final NetconfMessage message) {
        final Entry<Instant, XmlElement> stripped = NetconfMessageTransformUtil.stripNotification(message);
        final QName notificationNoRev;
        try {
            notificationNoRev = QName.create(
                    stripped.getValue().getNamespace(), stripped.getValue().getName()).withoutRevision();
        } catch (final MissingNameSpaceException e) {
            throw new IllegalArgumentException(
                    "Unable to parse notification " + message + ", cannot find namespace", e);
        }

        final var matchingTopLevel = mappedNotifications.get(notificationNoRev);
        final var element = stripped.getValue().getDomElement();
        if (!matchingTopLevel.isEmpty()) {
            final var notification = getMostRecentNotification(matchingTopLevel);
            // FIXME: we really should have a pre-constructed identifier here
            return new NetconfDeviceNotification(toNotification(Absolute.of(notification.getQName()), element),
                stripped.getKey());
        }

        final var nestedInfo = findNestedNotification(message, element)
            .orElseThrow(() -> new IllegalArgumentException("Unable to parse notification for " + element
                + ". Available notifications: " + mappedNotifications.keySet()));
        final var schemaPath = nestedInfo.schemaPath;
        return new NetconfDeviceTreeNotification(toNotification(schemaPath, nestedInfo.element), schemaPath,
            stripped.getKey(), nestedInfo.instancePath);
    }

    @GuardedBy("this")
    private ContainerNode toNotification(final Absolute notificationPath, final Element element) {
        final NormalizationResultHolder resultHolder = new NormalizationResultHolder();
        try {
            final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
            final XmlParserStream xmlParser = XmlParserStream.create(writer, mountContext,
                    SchemaInferenceStack.of(mountContext.getEffectiveModelContext(), notificationPath).toInference(),
                    strictParsing);
            xmlParser.traverse(new DOMSource(element));
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException
                | UnsupportedOperationException e) {
            throw new IllegalArgumentException(String.format("Failed to parse notification %s", element), e);
        }
        return (ContainerNode) resultHolder.getResult().data();
    }

    private Optional<NestedNotificationInfo> findNestedNotification(final NetconfMessage message,
            final Element element) {
        final Iterator<? extends Module> modules = mountContext.getEffectiveModelContext()
                .findModules(XMLNamespace.of(element.getNamespaceURI())).iterator();
        if (!modules.hasNext()) {
            throw new IllegalArgumentException(
                    "Unable to parse notification " + message + ", cannot find top level module");
        }
        final Module module = modules.next();
        final QName topLevelNodeQName = QName.create(element.getNamespaceURI(), element.getLocalName());
        for (DataSchemaNode childNode : module.getChildNodes()) {
            if (topLevelNodeQName.isEqualWithoutRevision(childNode.getQName())) {
                return Optional.of(traverseXmlNodeContainingNotification(element, childNode, new ArrayList<>(),
                    YangInstanceIdentifier.builder()));
            }
        }
        return Optional.empty();
    }

    // FIXME: this method is using QNames which are not bound to a Revision. Why is that?
    // FIXME: furthermore this does not handle the entirety of schema layout: notably missing a choice/case schema nodes
    private NestedNotificationInfo traverseXmlNodeContainingNotification(final Node xmlNode,
            final SchemaNode schemaNode, final List<QName> schemaBuilder,
            final InstanceIdentifierBuilder instanceBuilder) {
        if (schemaNode instanceof ContainerSchemaNode containerSchema) {
            instanceBuilder.node(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()));
            schemaBuilder.add(containerSchema.getQName());

            Entry<Node, SchemaNode> xmlContainerChildPair = findXmlContainerChildPair(xmlNode, containerSchema);
            return traverseXmlNodeContainingNotification(xmlContainerChildPair.getKey(),
                    xmlContainerChildPair.getValue(), schemaBuilder, instanceBuilder);
        } else if (schemaNode instanceof ListSchemaNode listSchema) {
            instanceBuilder.node(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()));
            schemaBuilder.add(listSchema.getQName());

            Map<QName, Object> listKeys = findXmlListKeys(xmlNode, listSchema);
            instanceBuilder.nodeWithKey(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()), listKeys);

            Entry<Node, SchemaNode> xmlListChildPair = findXmlListChildPair(xmlNode, listSchema);
            return traverseXmlNodeContainingNotification(xmlListChildPair.getKey(),
                    xmlListChildPair.getValue(), schemaBuilder, instanceBuilder);
        } else if (schemaNode instanceof NotificationDefinition) {
            // FIXME: this should not be here: it does not form a valid YangInstanceIdentifier
            instanceBuilder.node(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()));
            schemaBuilder.add(schemaNode.getQName());

            return new NestedNotificationInfo(Absolute.of(schemaBuilder),
                    new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, instanceBuilder.build()), xmlNode);
        }
        throw new IllegalStateException("No notification found");
    }

    private static Entry<Node, SchemaNode> findXmlContainerChildPair(final Node xmlNode,
            final ContainerSchemaNode container) {
        final NodeList nodeList = xmlNode.getChildNodes();
        final Map<QName, SchemaNode> childrenWithoutRevision =
                Streams.concat(container.getChildNodes().stream(), container.getNotifications().stream())
                    .collect(Collectors.toMap(child -> child.getQName().withoutRevision(), Function.identity()));

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                QName currentNodeQName = QName.create(currentNode.getNamespaceURI(), currentNode.getLocalName());
                SchemaNode schemaChildNode = childrenWithoutRevision.get(currentNodeQName);
                if (schemaChildNode != null) {
                    return Map.entry(currentNode, schemaChildNode);
                }
            }
        }
        throw new IllegalStateException("No container child found.");
    }

    private static Map<QName, Object> findXmlListKeys(final Node xmlNode, final ListSchemaNode listSchemaNode) {
        Map<QName, Object> listKeys = new HashMap<>();
        NodeList nodeList = xmlNode.getChildNodes();
        Set<QName> keyDefinitionsWithoutRevision = listSchemaNode.getKeyDefinition().stream()
                .map(QName::withoutRevision).collect(Collectors.toSet());
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                QName currentNodeQName = QName.create(currentNode.getNamespaceURI(), currentNode.getLocalName());
                if (keyDefinitionsWithoutRevision.contains(currentNodeQName)) {
                    listKeys.put(currentNodeQName, currentNode.getFirstChild().getNodeValue());
                }
            }
        }
        if (listKeys.isEmpty()) {
            throw new IllegalStateException("Notification cannot be contained in list without key statement.");
        }
        return listKeys;
    }

    private static Entry<Node, SchemaNode> findXmlListChildPair(final Node xmlNode, final ListSchemaNode list) {
        final NodeList nodeList = xmlNode.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                QName currentNodeQName = QName.create(currentNode.getNamespaceURI(), currentNode.getLocalName());
                for (SchemaNode childNode : Iterables.concat(list.getChildNodes(), list.getNotifications())) {
                    if (!list.getKeyDefinition().contains(childNode.getQName())
                            && currentNodeQName.isEqualWithoutRevision(childNode.getQName())) {
                        return new AbstractMap.SimpleEntry<>(currentNode, childNode);
                    }
                }
            }
        }
        throw new IllegalStateException("No list child found.");
    }

    private static NotificationDefinition getMostRecentNotification(
            final Collection<? extends NotificationDefinition> notificationDefinitions) {
        return Collections.max(notificationDefinitions, (o1, o2) ->
            Revision.compare(o1.getQName().getRevision(), o2.getQName().getRevision()));
    }

    @Override
    public NetconfMessage toRpcRequest(final QName rpc, final ContainerNode payload) {
        // In case no input for rpc is defined, we can simply construct the payload here

        // Determine whether a base netconf operation is being invoked
        // and also check if the device exposed model for base netconf.
        // If no, use pre built base netconf operations model
        final boolean needToUseBaseCtx = mappedRpcs.get(rpc) == null && isBaseOrNotificationRpc(rpc);
        final ImmutableMap<QName, ? extends RpcDefinition> currentMappedRpcs;
        if (needToUseBaseCtx) {
            currentMappedRpcs = baseSchema.getMappedRpcs();
        } else {
            currentMappedRpcs = mappedRpcs;
        }

        final RpcDefinition mappedRpc = checkNotNull(currentMappedRpcs.get(rpc),
                "Unknown rpc %s, available rpcs: %s", rpc, currentMappedRpcs.keySet());
        if (mappedRpc.getInput().getChildNodes().isEmpty()) {
            return new NetconfMessage(NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpc, counter)
                .getNode().getOwnerDocument());
        }

        checkNotNull(payload, "Transforming an rpc with input: %s, payload cannot be null", rpc);

        final DOMResult result = NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpc, counter);
        try {
            // If the schema context for netconf device does not contain model for base netconf operations,
            // use default pre build context with just the base model
            // This way operations like lock/unlock are supported even if the source for base model was not provided
            final EffectiveModelContext ctx = needToUseBaseCtx ? baseSchema.getEffectiveModelContext()
                    : mountContext.getEffectiveModelContext();
            NetconfMessageTransformUtil.writeNormalizedOperationInput(payload, result, Absolute.of(rpc),
                ctx);
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize input of " + rpc, e);
        }

        final Document node = result.getNode().getOwnerDocument();

        return new NetconfMessage(node);
    }

    @Override
    public NetconfMessage toActionRequest(final Absolute action, final DOMDataTreeIdentifier domDataTreeIdentifier,
            final NormalizedNode payload) {
        final ActionDefinition actionDef = actions.get(action);
        checkArgument(actionDef != null, "Action does not exist: %s", action);

        final InputSchemaNode inputDef = actionDef.getInput();
        if (inputDef.getChildNodes().isEmpty()) {
            return new NetconfMessage(NetconfMessageTransformUtil.prepareDomResultForActionRequest(contextTree,
                domDataTreeIdentifier, counter, actionDef.getQName()).getNode().getOwnerDocument());
        }

        checkNotNull(payload, "Transforming an action with input: %s, payload cannot be null", action);
        checkArgument(payload instanceof ContainerNode,
                "Transforming an action with input: %s, payload has to be a container, but was: %s", action, payload);

        final DOMResult result = NetconfMessageTransformUtil.prepareDomResultForActionRequest(contextTree,
            domDataTreeIdentifier, counter, actionDef.getQName());
        try {
            NetconfMessageTransformUtil.writeNormalizedOperationInput((ContainerNode) payload, result, action,
                mountContext.getEffectiveModelContext());
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize input of " + action, e);
        }

        return new NetconfMessage(result.getNode().getOwnerDocument());
    }

    private static boolean isBaseOrNotificationRpc(final QName rpc) {
        return BASE_OR_NOTIFICATION_NS.contains(rpc.getNamespace());
    }

    @Override
    public synchronized DOMRpcResult toRpcResult(final RpcResult<NetconfMessage> resultPayload, final QName rpc) {
        if (!resultPayload.isSuccessful()) {
            return new DefaultDOMRpcResult(resultPayload.getErrors());
        }

        final var message = resultPayload.getResult();
        final ContainerNode normalizedNode;
        if (NetconfMessageTransformUtil.isDataRetrievalOperation(rpc)) {
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
            final ImmutableMap<QName, ? extends RpcDefinition> currentMappedRpcs;
            if (mappedRpcs.get(rpc) == null && isBaseOrNotificationRpc(rpc)) {
                currentMappedRpcs = baseSchema.getMappedRpcs();
            } else {
                currentMappedRpcs = mappedRpcs;
            }

            final RpcDefinition rpcDefinition = currentMappedRpcs.get(rpc);
            checkArgument(rpcDefinition != null, "Unable to parse response of %s, the rpc is unknown", rpc);

            // In case no input for rpc is defined, we can simply construct the payload here
            normalizedNode = parseResult(message, Absolute.of(rpc), rpcDefinition);
        }
        return new DefaultDOMRpcResult(normalizedNode);
    }

    @Override
    public DOMActionResult toActionResult(final Absolute action, final NetconfMessage message) {
        final ActionDefinition actionDefinition = actions.get(action);
        checkArgument(actionDefinition != null, "Action does not exist: %s", action);
        final ContainerNode normalizedNode = parseResult(message, action, actionDefinition);

        if (normalizedNode == null) {
            return new SimpleDOMActionResult(List.of());
        } else {
            return new SimpleDOMActionResult(normalizedNode, List.of());
        }
    }

    private ContainerNode parseResult(final NetconfMessage message, final Absolute operationPath,
            final OperationDefinition operationDef) {
        final Optional<XmlElement> okResponseElement = XmlElement.fromDomDocument(message.getDocument())
                .getOnlyChildElementWithSameNamespaceOptionally("ok");
        final var operOutput = operationDef.getOutput();
        if (operOutput.getChildNodes().isEmpty()) {
            checkArgument(okResponseElement.isPresent(), "Unexpected content in response of operation: %s, %s",
                operationDef.getQName(), message);
            return null;
        }
        if (okResponseElement.isPresent()) {
            // FIXME: could be an action as well
            LOG.debug("Received response <ok/> for RPC with defined Output");
            return null;
        }

        final var operSteps = operationPath.getNodeIdentifiers();
        final var outputPath = Absolute.of(ImmutableList.<QName>builderWithExpectedSize(operSteps.size() + 1)
            .addAll(operSteps)
            .add(operOutput.getQName())
            .build());
        // FIXME: we should have a cached inference here, or XMLParserStream should accept Absolute instead
        final var inference = SchemaInferenceStack.of(mountContext.getEffectiveModelContext(), outputPath)
            .toInference();

        final NormalizationResultHolder resultHolder = new NormalizationResultHolder();
        final Element element = message.getDocument().getDocumentElement();
        try {
            final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
            final XmlParserStream xmlParser = XmlParserStream.create(writer, mountContext, inference, strictParsing);
            xmlParser.traverse(new DOMSource(element));
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            throw new IllegalArgumentException(String.format("Failed to parse RPC response %s", element), e);
        }
        return (ContainerNode) resultHolder.getResult().data();
    }

    @Beta
    public static class NetconfDeviceNotification implements DOMNotification, DOMEvent {
        private final ContainerNode content;
        private final Absolute schemaPath;
        private final Instant eventTime;

        NetconfDeviceNotification(final ContainerNode content, final Instant eventTime) {
            this.content = content;
            this.eventTime = eventTime;
            schemaPath = Absolute.of(content.name().getNodeType());
        }

        NetconfDeviceNotification(final ContainerNode content, final Absolute schemaPath, final Instant eventTime) {
            this.content = content;
            this.eventTime = eventTime;
            this.schemaPath = schemaPath;
        }

        @Override
        public Absolute getType() {
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

    @Beta
    public static class NetconfDeviceTreeNotification extends NetconfDeviceNotification {
        private final DOMDataTreeIdentifier domDataTreeIdentifier;

        NetconfDeviceTreeNotification(final ContainerNode content, final Absolute schemaPath, final Instant eventTime,
                final DOMDataTreeIdentifier domDataTreeIdentifier) {
            super(content, schemaPath, eventTime);
            this.domDataTreeIdentifier = domDataTreeIdentifier;
        }

        public DOMDataTreeIdentifier getDomDataTreeIdentifier() {
            return domDataTreeIdentifier;
        }
    }

    private static final class NestedNotificationInfo {
        final @NonNull DOMDataTreeIdentifier instancePath;
        final @NonNull Absolute schemaPath;
        final @NonNull Element element;

        NestedNotificationInfo(final Absolute schemaPath, final DOMDataTreeIdentifier instancePath,
                final Node documentNode) {
            this.schemaPath = requireNonNull(schemaPath);
            this.instancePath = requireNonNull(instancePath);
            checkArgument(documentNode instanceof Element, "Unexpected document node %s", documentNode);
            element = (Element) documentNode;
        }
    }
}

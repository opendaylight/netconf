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
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.MissingNameSpaceException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.client.mdsal.api.ActionTransformer;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.NotificationTransformer;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetconfMessageTransformer
        implements ActionTransformer, NotificationTransformer, RpcTransformer<ContainerNode, DOMRpcResult> {
    @NonNullByDefault
    private record NestedNotificationInfo(Absolute type, YangInstanceIdentifier path, Element element) {
        NestedNotificationInfo {
            requireNonNull(type);
            requireNonNull(path);
            requireNonNull(element);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageTransformer.class);

    private static final ImmutableSet<XMLNamespace> BASE_OR_NOTIFICATION_NS = ImmutableSet.of(
        GetConfig.QNAME.getNamespace(),
        NetconfCapabilityChange.QNAME.getNamespace(),
        CreateSubscription.QNAME.getNamespace());

    private final DatabindContext databind;
    private final BaseNetconfSchema baseSchema;
    private final MessageCounter counter = new MessageCounter();
    private final ImmutableMap<QName, ? extends RpcDefinition> mappedRpcs;
    private final Multimap<QName, ? extends NotificationDefinition> mappedNotifications;
    private final boolean strictParsing;
    private final ImmutableMap<Absolute, ActionDefinition> actions;

    public NetconfMessageTransformer(final DatabindContext databind, final boolean strictParsing,
                                     final BaseNetconfSchema baseSchema) {
        this.databind = requireNonNull(databind);

        final var modelContext = databind.modelContext();
        mappedRpcs = Maps.uniqueIndex(modelContext.getOperations(), SchemaNode::getQName);
        actions = getActions(modelContext);

        // RFC6020 normal notifications
        mappedNotifications = Multimaps.index(modelContext.getNotifications(),
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
        if (dataSchemaNode instanceof ActionNodeContainer actionContainer) {
            for (var actionDefinition : actionContainer.getActions()) {
                path.addLast(actionDefinition.getQName());
                builder.put(Absolute.of(path), actionDefinition);
                path.removeLast();
            }
        }
        if (dataSchemaNode instanceof DataNodeContainer dataContainer) {
            for (var innerDataSchemaNode : dataContainer.getChildNodes()) {
                path.addLast(innerDataSchemaNode.getQName());
                findAction(innerDataSchemaNode, path, builder);
                path.removeLast();
            }
        } else if (dataSchemaNode instanceof ChoiceSchemaNode choice) {
            for (var caze : choice.getCases()) {
                path.addLast(caze.getQName());
                findAction(caze, path, builder);
                path.removeLast();
            }
        }
    }

    @Override
    public synchronized DOMNotificationEvent toNotification(final NetconfMessage message) {
        final var stripped = NetconfMessageTransformUtil.stripNotification(message);
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
            return new DOMNotificationEvent.Rfc6020(toNotification(Absolute.of(notification.getQName()), element),
                stripped.getKey());
        }

        final var nestedInfo = findNestedNotification(message, element)
            .orElseThrow(() -> new IllegalArgumentException("Unable to parse notification for " + element
                + ". Available notifications: " + mappedNotifications.keySet()));
        return new DOMNotificationEvent.Rfc7950(nestedInfo.type, nestedInfo.path,
            toNotification(nestedInfo.type, nestedInfo.element), stripped.getKey());
    }

    @GuardedBy("this")
    @NonNullByDefault
    private ContainerNode toNotification(final Absolute notificationPath, final Element element) {
        final var resultHolder = new NormalizationResultHolder();
        try {
            final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
            final var xmlParser = XmlParserStream.create(writer, databind.mountContext(),
                SchemaInferenceStack.of(databind.modelContext(), notificationPath).toInference(), strictParsing);
            xmlParser.traverse(new DOMSource(element));
        } catch (XMLStreamException | IOException | UnsupportedOperationException e) {
            throw new IllegalArgumentException(String.format("Failed to parse notification %s", element), e);
        }
        return (ContainerNode) resultHolder.getResult().data();
    }

    private Optional<NestedNotificationInfo> findNestedNotification(final NetconfMessage message,
            final Element element) {
        final var modules = databind.modelContext().findModules(XMLNamespace.of(element.getNamespaceURI()))
            .iterator();
        if (!modules.hasNext()) {
            throw new IllegalArgumentException(
                    "Unable to parse notification " + message + ", cannot find top level module");
        }
        final var module = modules.next();
        final var topLevelNodeQName = QName.create(element.getNamespaceURI(), element.getLocalName());
        for (var childNode : module.getChildNodes()) {
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

            final var xmlContainerChildPair = findXmlContainerChildPair(xmlNode, containerSchema);
            return traverseXmlNodeContainingNotification(xmlContainerChildPair.getKey(),
                    xmlContainerChildPair.getValue(), schemaBuilder, instanceBuilder);
        } else if (schemaNode instanceof ListSchemaNode listSchema) {
            instanceBuilder.node(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()));
            schemaBuilder.add(listSchema.getQName());

            final var listKeys = findXmlListKeys(xmlNode, listSchema);
            instanceBuilder.nodeWithKey(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()), listKeys);

            final var xmlListChildPair = findXmlListChildPair(xmlNode, listSchema);
            return traverseXmlNodeContainingNotification(xmlListChildPair.getKey(),
                    xmlListChildPair.getValue(), schemaBuilder, instanceBuilder);
        } else if (schemaNode instanceof NotificationDefinition notifSchema) {
            // FIXME: this should not be here: it does not form a valid YangInstanceIdentifier
            instanceBuilder.node(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()));
            schemaBuilder.add(notifSchema.getQName());

            if (xmlNode instanceof Element element) {
                return new NestedNotificationInfo(Absolute.of(schemaBuilder), instanceBuilder.build(), element);
            }
            throw new IllegalArgumentException("Unexpected document node " + xmlNode);
        }
        throw new IllegalStateException("No notification found");
    }

    private static Entry<Node, SchemaNode> findXmlContainerChildPair(final Node xmlNode,
            final ContainerSchemaNode container) {
        final var nodeList = xmlNode.getChildNodes();
        final var childrenWithoutRevision = Streams.concat(
            container.getChildNodes().stream(),
            container.getNotifications().stream())
            .collect(Collectors.toMap(child -> child.getQName().withoutRevision(), Function.identity()));

        for (int i = 0; i < nodeList.getLength(); i++) {
            final var currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                final var currentNodeQName = QName.create(currentNode.getNamespaceURI(), currentNode.getLocalName());
                final var schemaChildNode = childrenWithoutRevision.get(currentNodeQName);
                if (schemaChildNode != null) {
                    return Map.entry(currentNode, schemaChildNode);
                }
            }
        }
        throw new IllegalStateException("No container child found.");
    }

    private static Map<QName, Object> findXmlListKeys(final Node xmlNode, final ListSchemaNode listSchemaNode) {
        final var listKeys = new HashMap<QName, Object>();
        final var nodeList = xmlNode.getChildNodes();
        final var keyDefinitionsWithoutRevision = listSchemaNode.getKeyDefinition().stream()
                .map(QName::withoutRevision)
                .collect(Collectors.toSet());
        for (int i = 0; i < nodeList.getLength(); i++) {
            final var currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                final var currentNodeQName = QName.create(currentNode.getNamespaceURI(), currentNode.getLocalName());
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
        final var nodeList = xmlNode.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            final var currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                final var currentNodeQName = QName.create(currentNode.getNamespaceURI(), currentNode.getLocalName());
                for (var childNode : Iterables.concat(list.getChildNodes(), list.getNotifications())) {
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
        final var currentMappedRpcs = needToUseBaseCtx ? baseSchema.mappedRpcs() : mappedRpcs;

        final var mappedRpc = checkNotNull(currentMappedRpcs.get(rpc),
                "Unknown rpc %s, available rpcs: %s", rpc, currentMappedRpcs.keySet());
        if (mappedRpc.getInput().getChildNodes().isEmpty()) {
            return new NetconfMessage(NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpc, counter)
                .getNode().getOwnerDocument());
        }

        checkNotNull(payload, "Transforming an rpc with input: %s, payload cannot be null", rpc);

        final var result = NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpc, counter);
        try {
            // If the schema context for netconf device does not contain model for base netconf operations,
            // use default pre build context with just the base model
            // This way operations like lock/unlock are supported even if the source for base model was not provided
            final var modelContext = needToUseBaseCtx ? baseSchema.modelContext() : databind.modelContext();
            NetconfMessageTransformUtil.writeNormalizedOperationInput(payload, result, Absolute.of(rpc), modelContext);
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize input of " + rpc, e);
        }

        return new NetconfMessage(result.getNode().getOwnerDocument());
    }

    @Override
    public NetconfMessage toActionRequest(final Absolute action, final DOMDataTreeIdentifier domDataTreeIdentifier,
            final NormalizedNode payload) {
        final var actionDef = actions.get(action);
        checkArgument(actionDef != null, "Action does not exist: %s", action);

        final var inputDef = actionDef.getInput();
        if (inputDef.getChildNodes().isEmpty()) {
            return new NetconfMessage(NetconfMessageTransformUtil.prepareDomResultForActionRequest(
                databind.schemaTree(), domDataTreeIdentifier, counter,
                actionDef.getQName()).getNode().getOwnerDocument());
        }

        checkNotNull(payload, "Transforming an action with input: %s, payload cannot be null", action);
        checkArgument(payload instanceof ContainerNode,
                "Transforming an action with input: %s, payload has to be a container, but was: %s", action, payload);

        final var result = NetconfMessageTransformUtil.prepareDomResultForActionRequest(databind.schemaTree(),
            domDataTreeIdentifier, counter, actionDef.getQName());
        try {
            NetconfMessageTransformUtil.writeNormalizedOperationInput((ContainerNode) payload, result, action,
                databind.modelContext());
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
        // Determine whether a base netconf operation is being invoked
        // and also check if the device exposed model for base netconf.
        // If no, use pre built base netconf operations model
        final ImmutableMap<QName, ? extends RpcDefinition> currentMappedRpcs;
        if (mappedRpcs.get(rpc) == null && isBaseOrNotificationRpc(rpc)) {
            currentMappedRpcs = baseSchema.mappedRpcs();
        } else {
            currentMappedRpcs = mappedRpcs;
        }

        final var rpcDefinition = currentMappedRpcs.get(rpc);
        checkArgument(rpcDefinition != null, "Unable to parse response of %s, the rpc is unknown", rpc);

        // In case no input for rpc is defined, we can simply construct the payload here
        return new DefaultDOMRpcResult(parseResult(message, Absolute.of(rpc), rpcDefinition));
    }

    @Override
    public DOMRpcResult toActionResult(final Absolute action, final NetconfMessage message) {
        final var actionDefinition = actions.get(action);
        checkArgument(actionDefinition != null, "Action does not exist: %s", action);
        final var normalizedNode = parseResult(message, action, actionDefinition);

        return normalizedNode == null ? new DefaultDOMRpcResult(List.of())
            : new DefaultDOMRpcResult(normalizedNode, List.of());
    }

    private ContainerNode parseResult(final NetconfMessage message, final Absolute operationPath,
            final OperationDefinition operationDef) {
        final var doc = message.getDocument();
        final var okResponseElement = XmlElement.fromDomDocument(doc)
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

        // We are about to parse a <rpc-reply> element into YANG world.
        // Remove "message-id" attribute, as that is it does not have a representation there.
        final var element = doc.getDocumentElement();
        element.removeAttribute(XmlNetconfConstants.MESSAGE_ID);

        final var operSteps = operationPath.getNodeIdentifiers();
        final var outputPath = Absolute.of(ImmutableList.<QName>builderWithExpectedSize(operSteps.size() + 1)
            .addAll(operSteps)
            .add(operOutput.getQName())
            .build());

        final var resultHolder = new NormalizationResultHolder();
        try {
            final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
            final var xmlParser = XmlParserStream.create(writer, databind.xmlCodecs(), outputPath, strictParsing);
            xmlParser.traverse(new DOMSource(element));
        } catch (XMLStreamException | IOException e) {
            throw new IllegalArgumentException(String.format("Failed to parse RPC response %s", element), e);
        }
        return (ContainerNode) resultHolder.getResult().data();
    }
}

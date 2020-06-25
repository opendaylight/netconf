/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.IETF_NETCONF_NOTIFICATIONS;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_URI;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
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
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
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
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.NotificationNodeContainer;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class NetconfMessageTransformer implements MessageTransformer<NetconfMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageTransformer.class);

    private static final ImmutableSet<URI> BASE_OR_NOTIFICATION_NS = ImmutableSet.of(
        NETCONF_URI,
        IETF_NETCONF_NOTIFICATIONS.getNamespace(),
        CREATE_SUBSCRIPTION_RPC_QNAME.getNamespace());

    private final MountPointContext mountContext;
    private final DataSchemaContextTree contextTree;
    private final BaseSchema baseSchema;
    private final MessageCounter counter;
    private final ImmutableMap<QName, RpcDefinition> mappedRpcs;
    private final Multimap<SchemaPath, NotificationDefinition> mappedNotifications;
    private final boolean strictParsing;
    private final ImmutableMap<SchemaPath, ActionDefinition> actions;

    public NetconfMessageTransformer(final MountPointContext mountContext, final boolean strictParsing) {
        this(mountContext, strictParsing, BaseSchema.BASE_NETCONF_CTX);
    }

    public NetconfMessageTransformer(final MountPointContext mountContext, final boolean strictParsing,
                                     final BaseSchema baseSchema) {
        this.counter = new MessageCounter();
        this.mountContext = requireNonNull(mountContext);

        final SchemaContext schemaContext = mountContext.getSchemaContext();
        this.contextTree = DataSchemaContextTree.from(schemaContext);

        this.mappedRpcs = Maps.uniqueIndex(schemaContext.getOperations(), SchemaNode::getQName);
        this.actions = Maps.uniqueIndex(getActions(schemaContext), ActionDefinition::getPath);
        this.mappedNotifications = Multimaps.index(getNotifications(schemaContext),
            node -> removeRevisions(node.getPath()));
        this.baseSchema = baseSchema;
        this.strictParsing = strictParsing;
    }

    @VisibleForTesting
    static List<ActionDefinition> getActions(final SchemaContext schemaContext) {
        final List<ActionDefinition> builder = new ArrayList<>();
        findAction(schemaContext, builder);
        return builder;
    }

    @VisibleForTesting
    static List<NotificationDefinition> getNotifications(final SchemaContext schemaContext) {
        final List<NotificationDefinition> builder = new ArrayList<>();
        findNotification(schemaContext, builder);
        return builder;
    }

    private static void findNotification(final DataSchemaNode dataSchemaNode,
            final List<NotificationDefinition> builder) {
        if (dataSchemaNode instanceof NotificationNodeContainer) {
            for (NotificationDefinition notifDef : ((NotificationNodeContainer) dataSchemaNode).getNotifications()) {
                builder.add(notifDef);
            }
        }
        if (dataSchemaNode instanceof DataNodeContainer) {
            for (DataSchemaNode innerDataSchemaNode : ((DataNodeContainer) dataSchemaNode).getChildNodes()) {
                findNotification(innerDataSchemaNode, builder);
            }
        } else if (dataSchemaNode instanceof ChoiceSchemaNode) {
            for (CaseSchemaNode caze : ((ChoiceSchemaNode) dataSchemaNode).getCases().values()) {
                findNotification(caze, builder);
            }
        }
    }

    private static void findAction(final DataSchemaNode dataSchemaNode, final List<ActionDefinition> builder) {
        if (dataSchemaNode instanceof ActionNodeContainer) {
            for (ActionDefinition actionDefinition : ((ActionNodeContainer) dataSchemaNode).getActions()) {
                builder.add(actionDefinition);
            }
        }
        if (dataSchemaNode instanceof DataNodeContainer) {
            for (DataSchemaNode innerDataSchemaNode : ((DataNodeContainer) dataSchemaNode).getChildNodes()) {
                findAction(innerDataSchemaNode, builder);
            }
        } else if (dataSchemaNode instanceof ChoiceSchemaNode) {
            for (CaseSchemaNode caze : ((ChoiceSchemaNode) dataSchemaNode).getCases().values()) {
                findAction(caze, builder);
            }
        }
    }

    private SchemaPath removeRevisions(SchemaPath originalSchemaPath) {
        List<QName> schemaPaths = new ArrayList<>();
        originalSchemaPath.getPathFromRoot().forEach(qName -> {
            schemaPaths.add(qName.withoutRevision());
        });
        return SchemaPath.create(originalSchemaPath.isAbsolute(),
                Arrays.copyOf(schemaPaths.toArray(), schemaPaths.toArray().length, QName[].class));
    }

    @Override
    public synchronized DOMNotification toNotification(NetconfMessage message) {
        final Map.Entry<Instant, XmlElement> stripped = NetconfMessageTransformUtil.stripNotification(message);
        final QName notificationNoRev;
        NestedNotificationInfo nestedNotificationInfo = null;
        try {
            notificationNoRev = QName.create(
                    stripped.getValue().getNamespace(), stripped.getValue().getName()).withoutRevision();
        } catch (final MissingNameSpaceException e) {
            throw new IllegalArgumentException(
                    "Unable to parse notification " + message + ", cannot find namespace", e);
        }
        Collection<NotificationDefinition> notificationDefinitions =
                mappedNotifications.get(SchemaPath.create(true, notificationNoRev));
        Element element = stripped.getValue().getDomElement();

        if (notificationDefinitions.isEmpty()) {
            // check if notification is nested notification
            Optional<NestedNotificationInfo> nestedNotificationOptional = findNestedNotification(message, element);
            if (nestedNotificationOptional.isPresent()) {
                nestedNotificationInfo = nestedNotificationOptional.get();
                notificationDefinitions = Collections.singletonList(nestedNotificationInfo.notificationDefinition);
                element = (Element) nestedNotificationInfo.notificationNode;
            }
        }
        Preconditions.checkArgument(notificationDefinitions.size() > 0,
                "Unable to parse notification %s, unknown notification. Available notifications: %s",
                notificationDefinitions, mappedNotifications.keySet());

        final NotificationDefinition mostRecentNotification = getMostRecentNotification(notificationDefinitions);

        final ContainerSchemaNode notificationAsContainerSchemaNode =
                NetconfMessageTransformUtil.createSchemaForNotification(mostRecentNotification);

        final ContainerNode content;
        try {
            final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
            final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
            final XmlParserStream xmlParser = XmlParserStream.create(writer, mountContext,
                    notificationAsContainerSchemaNode, strictParsing);
            xmlParser.traverse(new DOMSource(element));
            content = (ContainerNode) resultHolder.getResult();
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException
                | UnsupportedOperationException e) {
            throw new IllegalArgumentException(String.format("Failed to parse notification %s", element), e);
        }
        if (nestedNotificationInfo != null) {
            return new NetconfDeviceTreeNotification(content, nestedNotificationInfo.notificationDefinition,
                    mostRecentNotification.getPath(), nestedNotificationInfo.domDataTreeIdentifier , stripped.getKey());
        } else {
            return new NetconfDeviceNotification(content, stripped.getKey());
        }
    }

    private Optional<NestedNotificationInfo> findNestedNotification(NetconfMessage message, Element element) {
        Set<Module> modules = mountContext.getSchemaContext().findModules(URI.create(element.getNamespaceURI()));
        if (modules.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unable to parse notification " + message + ", cannot find top level module");
        }
        Module module = getMostRecentModule(modules);
        QName topLevelNodeQName = QName.create(element.getNamespaceURI(), element.getLocalName());
        for (DataSchemaNode childNode : module.getChildNodes()) {
            if (topLevelNodeQName.isEqualWithoutRevision(childNode.getQName())) {
                YangInstanceIdentifier.InstanceIdentifierBuilder builder = YangInstanceIdentifier.builder();
                return Optional.of(traverseXmlNodeContainingNotification(element, childNode, builder));
            }
        }
        return Optional.empty();
    }

    private NestedNotificationInfo traverseXmlNodeContainingNotification(Node xmlNode, SchemaNode schemaNode,
            YangInstanceIdentifier.InstanceIdentifierBuilder builder) {
        if (schemaNode instanceof ContainerSchemaNode) {
            ContainerSchemaNode dataContainerNode = (ContainerSchemaNode) schemaNode;
            builder.node(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()));

            Map.Entry<Node, SchemaNode> xmlContainerChildPair = findXmlContainerChildPair(xmlNode, dataContainerNode);
            return traverseXmlNodeContainingNotification(xmlContainerChildPair.getKey(),
                    xmlContainerChildPair.getValue(), builder);
        } else if (schemaNode instanceof ListSchemaNode) {
            ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
            builder.node(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()));

            Map<QName, Object> listKeys = findXmlListKeys(xmlNode, listSchemaNode);
            builder.nodeWithKey(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()), listKeys);

            Map.Entry<Node, SchemaNode> xmlListChildPair = findXmlListChildPair(xmlNode, listSchemaNode);
            return traverseXmlNodeContainingNotification(xmlListChildPair.getKey(),
                    xmlListChildPair.getValue(), builder);
        } else if (schemaNode instanceof NotificationDefinition) {
            builder.node(QName.create(xmlNode.getNamespaceURI(), xmlNode.getLocalName()));

            NotificationDefinition notificationDefinition = (NotificationDefinition) schemaNode;
            return new NestedNotificationInfo(notificationDefinition,
                    new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, builder.build()), xmlNode);
        }
        throw new IllegalStateException("No notification found");
    }

    private Map.Entry<Node, SchemaNode> findXmlContainerChildPair(Node xmlNode, ContainerSchemaNode dataContainerNode) {
        NodeList nodeList = xmlNode.getChildNodes();
        Map<QName, SchemaNode> childrenWithoutRevision = new HashMap<>();
        dataContainerNode.getChildNodes()
                .forEach(child -> childrenWithoutRevision.put(child.getQName().withoutRevision(), child));
        dataContainerNode.getNotifications()
                .forEach(notification -> childrenWithoutRevision.put(notification.getQName().withoutRevision(),
                        notification));
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                QName currentNodeQName = QName.create(currentNode.getNamespaceURI(), currentNode.getLocalName());
                SchemaNode schemaChildNode = childrenWithoutRevision.get(currentNodeQName);
                if (schemaChildNode != null) {
                    return new AbstractMap.SimpleEntry<>(currentNode, schemaChildNode);
                }
            }
        }
        throw new IllegalStateException("No container child found.");
    }

    private Map<QName, Object> findXmlListKeys(Node xmlNode, ListSchemaNode listSchemaNode) {
        Map<QName, Object> listKeys = new HashMap<>();
        NodeList nodeList = xmlNode.getChildNodes();
        Set<QName> keyDefinitionsWithoutRevision = new HashSet<>();
        listSchemaNode.getKeyDefinition()
                .forEach(keyDefinition -> keyDefinitionsWithoutRevision.add(keyDefinition.withoutRevision()));
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

    private Map.Entry<Node, SchemaNode> findXmlListChildPair(Node xmlNode, ListSchemaNode listSchemaNode) {
        NodeList nodeList = xmlNode.getChildNodes();
        Collection<SchemaNode> children = new ArrayList<>();
        children.addAll(listSchemaNode.getChildNodes());
        children.addAll(listSchemaNode.getNotifications());
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                QName currentNodeQName = QName.create(currentNode.getNamespaceURI(), currentNode.getLocalName());
                for (SchemaNode childNode : children) {
                    if (!listSchemaNode.getKeyDefinition().contains(childNode.getQName())
                            && currentNodeQName.isEqualWithoutRevision(childNode.getQName())) {
                        return new AbstractMap.SimpleEntry<>(currentNode, childNode);
                    }
                }
            }
        }
        throw new IllegalStateException("No list child found.");
    }

    private static NotificationDefinition getMostRecentNotification(
            final Collection<NotificationDefinition> notificationDefinitions) {
        return Collections.max(notificationDefinitions, (o1, o2) ->
            Revision.compare(o1.getQName().getRevision(), o2.getQName().getRevision()));
    }

    private static Module getMostRecentModule(final Collection<Module> modules) {
        return Collections.max(modules, (o1, o2) -> Revision.compare(o1.getRevision(), o2.getRevision()));
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
            final SchemaContext ctx = needToUseBaseCtx ? baseSchema.getSchemaContext()
                    : mountContext.getSchemaContext();
            NetconfMessageTransformUtil.writeNormalizedRpc((ContainerNode) payload, result, rpcInput, ctx);
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize " + rpcInput, e);
        }

        final Document node = result.getNode().getOwnerDocument();

        return new NetconfMessage(node);
    }

    @Override
    public NetconfMessage toActionRequest(final SchemaPath action, final DOMDataTreeIdentifier domDataTreeIdentifier,
            final NormalizedNode<?, ?> payload) {
        final ActionDefinition actionDef = actions.get(action);
        Preconditions.checkArgument(actionDef != null, "Action does not exist: %s", action);

        final ContainerSchemaNode inputDef = actionDef.getInput();
        if (inputDef.getChildNodes().isEmpty()) {
            return new NetconfMessage(NetconfMessageTransformUtil.prepareDomResultForActionRequest(contextTree,
                domDataTreeIdentifier, action, counter,
                actionDef.getQName()).getNode().getOwnerDocument());
        }

        Preconditions.checkNotNull(payload, "Transforming an action with input: %s, payload cannot be null", action);
        Preconditions.checkArgument(payload instanceof ContainerNode,
                "Transforming an action with input: %s, payload has to be a container, but was: %s", action, payload);

        // Set the path to the input of action for the node stream writer
        final DOMResult result = NetconfMessageTransformUtil.prepareDomResultForActionRequest(contextTree,
            domDataTreeIdentifier, inputDef.getPath(), counter, actionDef.getQName());

        try {
            NetconfMessageTransformUtil.writeNormalizedRpc((ContainerNode) payload, result, inputDef.getPath(),
                mountContext.getSchemaContext());
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize " + action, e);
        }

        return new NetconfMessage(result.getNode().getOwnerDocument());
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
        final ActionDefinition actionDefinition = actions.get(action);
        Preconditions.checkArgument(actionDefinition != null, "Action does not exist: %s", action);
        final ContainerNode normalizedNode = (ContainerNode) parseResult(message, actionDefinition);

        if (normalizedNode == null) {
            return new SimpleDOMActionResult(Collections.emptyList());
        } else {
            return new SimpleDOMActionResult(normalizedNode, Collections.emptyList());
        }
    }

    private NormalizedNode<?, ?> parseResult(final NetconfMessage message,
            final OperationDefinition operationDefinition) {
        final Optional<XmlElement> okResponseElement = XmlElement.fromDomDocument(message.getDocument())
                .getOnlyChildElementWithSameNamespaceOptionally("ok");
        if (operationDefinition.getOutput().getChildNodes().isEmpty()) {
            Preconditions.checkArgument(okResponseElement.isPresent(),
                "Unexpected content in response of rpc: %s, %s", operationDefinition.getQName(), message);
            return null;
        } else {
            if (okResponseElement.isPresent()) {
                LOG.debug("Received response <ok/> for RPC with defined Output");
                return null;
            }

            Element element = message.getDocument().getDocumentElement();
            try {
                final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
                final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
                final XmlParserStream xmlParser = XmlParserStream.create(writer, mountContext,
                        operationDefinition.getOutput(), strictParsing);
                xmlParser.traverse(new DOMSource(element));
                return resultHolder.getResult();
            } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
                throw new IllegalArgumentException(String.format("Failed to parse RPC response %s", element), e);
            }
        }
    }

    public static class NetconfDeviceNotification implements DOMNotification, DOMEvent {
        private final ContainerNode content;
        private final SchemaPath schemaPath;
        private final Instant eventTime;

        NetconfDeviceNotification(final ContainerNode content, final Instant eventTime) {
            this.content = content;
            this.eventTime = eventTime;
            this.schemaPath = toPath(content.getNodeType());
        }

        NetconfDeviceNotification(final ContainerNode content, final SchemaPath schemaPath, final Instant eventTime) {
            this.content = content;
            this.eventTime = eventTime;
            this.schemaPath = schemaPath;
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

    public static class NetconfDeviceTreeNotification extends NetconfDeviceNotification {
        private final NotificationDefinition notificationDefinition;
        private final DOMDataTreeIdentifier domDataTreeIdentifier;

        NetconfDeviceTreeNotification(final ContainerNode content, final NotificationDefinition notificationDefinition,
                final SchemaPath schemaPath, final DOMDataTreeIdentifier domDataTreeIdentifier,
                final Instant eventTime) {
            super(content, schemaPath, eventTime);
            this.notificationDefinition = notificationDefinition;
            this.domDataTreeIdentifier = domDataTreeIdentifier;
        }

        public DOMDataTreeIdentifier getDomDataTreeIdentifier() {
            return domDataTreeIdentifier;
        }

        public NotificationDefinition getNotificationDefinition() {
            return notificationDefinition;
        }
    }

    static class NestedNotificationInfo {
        private final NotificationDefinition notificationDefinition;
        private final DOMDataTreeIdentifier domDataTreeIdentifier;
        private final Node notificationNode;

        NestedNotificationInfo(NotificationDefinition notificationDefinition,
                DOMDataTreeIdentifier domDataTreeIdentifier, Node notificationNode) {
            this.notificationDefinition = notificationDefinition;
            this.domDataTreeIdentifier = domDataTreeIdentifier;
            this.notificationNode = notificationNode;
        }
    }
}

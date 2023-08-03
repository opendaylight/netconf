/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.common.mdsal.NormalizedDataUtil.NETCONF_DATA_QNAME;
import static org.opendaylight.netconf.common.mdsal.NormalizedDataUtil.NETCONF_QNAME;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.common.mdsal.NormalizedDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.EditContent;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedMetadata;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.YangInstanceIdentifierWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedMetadata;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaOrderedNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class NetconfMessageTransformUtil {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageTransformUtil.class);

    public static final String MESSAGE_ID_PREFIX = "m";

    public static final @NonNull QName CREATE_SUBSCRIPTION_RPC_QNAME =
            QName.create(CreateSubscriptionInput.QNAME, "create-subscription").intern();
    private static final String SUBTREE = "subtree";

    // Blank document used for creation of new DOM nodes
    private static final Document BLANK_DOCUMENT = XmlUtil.newDocument();

    public static final @NonNull QName IETF_NETCONF_MONITORING =
            QName.create(NetconfState.QNAME, "ietf-netconf-monitoring").intern();
    public static final @NonNull QName GET_DATA_QNAME = QName.create(IETF_NETCONF_MONITORING, "data").intern();
    public static final @NonNull QName GET_SCHEMA_QNAME = QName.create(IETF_NETCONF_MONITORING, "get-schema").intern();
    public static final @NonNull QName IETF_NETCONF_MONITORING_SCHEMA_FORMAT =
            QName.create(IETF_NETCONF_MONITORING, "format").intern();
    public static final @NonNull QName IETF_NETCONF_MONITORING_SCHEMA_LOCATION =
            QName.create(IETF_NETCONF_MONITORING, "location").intern();
    public static final @NonNull QName IETF_NETCONF_MONITORING_SCHEMA_IDENTIFIER =
            QName.create(IETF_NETCONF_MONITORING, "identifier").intern();
    public static final @NonNull QName IETF_NETCONF_MONITORING_SCHEMA_VERSION =
            QName.create(IETF_NETCONF_MONITORING, "version").intern();
    public static final @NonNull QName IETF_NETCONF_MONITORING_SCHEMA_NAMESPACE =
            QName.create(IETF_NETCONF_MONITORING, "namespace").intern();

    public static final @NonNull QName IETF_NETCONF_NOTIFICATIONS =
            QName.create(NetconfCapabilityChange.QNAME, "ietf-netconf-notifications").intern();

    public static final XMLNamespace NETCONF_URI = NETCONF_QNAME.getNamespace();

    public static final @NonNull NodeIdentifier NETCONF_DATA_NODEID = NodeIdentifier.create(NETCONF_DATA_QNAME);

    public static final @NonNull QName NETCONF_RPC_REPLY_QNAME =
        QName.create(NETCONF_QNAME, XmlNetconfConstants.RPC_REPLY_KEY).intern();
    public static final @NonNull NodeIdentifier NETCONF_RPC_REPLY_NODEID =
        NodeIdentifier.create(NETCONF_RPC_REPLY_QNAME);

    public static final @NonNull QName NETCONF_OK_QNAME = QName.create(NETCONF_QNAME, XmlNetconfConstants.OK).intern();
    public static final @NonNull QName NETCONF_ERROR_OPTION_QNAME =
        QName.create(NETCONF_QNAME, "error-option").intern();
    public static final @NonNull NodeIdentifier NETCONF_ERROR_OPTION_NODEID =
        NodeIdentifier.create(NETCONF_ERROR_OPTION_QNAME);
    public static final @NonNull QName NETCONF_RUNNING_QNAME = QName.create(NETCONF_QNAME, "running").intern();
    public static final @NonNull NodeIdentifier NETCONF_RUNNING_NODEID = NodeIdentifier.create(NETCONF_RUNNING_QNAME);
    public static final @NonNull QName NETCONF_SOURCE_QNAME = QName.create(NETCONF_QNAME, "source").intern();
    public static final @NonNull NodeIdentifier NETCONF_SOURCE_NODEID = NodeIdentifier.create(NETCONF_SOURCE_QNAME);
    public static final @NonNull QName NETCONF_CANDIDATE_QNAME = QName.create(NETCONF_QNAME, "candidate").intern();
    public static final @NonNull NodeIdentifier NETCONF_CANDIDATE_NODEID =
        NodeIdentifier.create(NETCONF_CANDIDATE_QNAME);
    public static final @NonNull QName NETCONF_TARGET_QNAME = QName.create(NETCONF_QNAME, "target").intern();
    public static final @NonNull NodeIdentifier NETCONF_TARGET_NODEID = NodeIdentifier.create(NETCONF_TARGET_QNAME);
    public static final @NonNull QName NETCONF_CONFIG_QNAME = QName.create(NETCONF_QNAME, "config").intern();
    public static final @NonNull NodeIdentifier NETCONF_CONFIG_NODEID = NodeIdentifier.create(NETCONF_CONFIG_QNAME);

    public static final @NonNull QName NETCONF_COMMIT_QNAME = QName.create(NETCONF_QNAME, "commit").intern();
    public static final @NonNull Absolute NETCONF_COMMIT_PATH = toPath(NETCONF_COMMIT_QNAME);
    public static final @NonNull QName NETCONF_VALIDATE_QNAME = QName.create(NETCONF_QNAME, "validate").intern();
    public static final @NonNull NodeIdentifier NETCONF_VALIDATE_NODEID = NodeIdentifier.create(NETCONF_VALIDATE_QNAME);
    public static final @NonNull Absolute NETCONF_VALIDATE_PATH = toPath(NETCONF_VALIDATE_QNAME);
    public static final @NonNull QName NETCONF_COPY_CONFIG_QNAME = QName.create(NETCONF_QNAME, "copy-config").intern();
    public static final @NonNull NodeIdentifier NETCONF_COPY_CONFIG_NODEID =
        NodeIdentifier.create(NETCONF_COPY_CONFIG_QNAME);
    public static final @NonNull Absolute NETCONF_COPY_CONFIG_PATH = toPath(NETCONF_COPY_CONFIG_QNAME);

    public static final @NonNull QName NETCONF_OPERATION_QNAME = QName.create(NETCONF_QNAME, "operation").intern();
    private static final @NonNull QName NETCONF_OPERATION_QNAME_LEGACY =
        NETCONF_OPERATION_QNAME.withoutRevision().intern();
    public static final @NonNull QName NETCONF_DEFAULT_OPERATION_QNAME =
            QName.create(NETCONF_OPERATION_QNAME, "default-operation").intern();
    public static final @NonNull NodeIdentifier NETCONF_DEFAULT_OPERATION_NODEID =
            NodeIdentifier.create(NETCONF_DEFAULT_OPERATION_QNAME);
    public static final @NonNull QName NETCONF_EDIT_CONFIG_QNAME = QName.create(NETCONF_QNAME, "edit-config").intern();
    public static final @NonNull NodeIdentifier NETCONF_EDIT_CONFIG_NODEID =
        NodeIdentifier.create(NETCONF_EDIT_CONFIG_QNAME);
    public static final @NonNull Absolute NETCONF_EDIT_CONFIG_PATH = toPath(NETCONF_EDIT_CONFIG_QNAME);
    public static final @NonNull QName NETCONF_GET_CONFIG_QNAME =
        QName.create(NETCONF_QNAME, XmlNetconfConstants.GET_CONFIG).intern();
    public static final @NonNull NodeIdentifier NETCONF_GET_CONFIG_NODEID =
        NodeIdentifier.create(NETCONF_GET_CONFIG_QNAME);
    public static final @NonNull Absolute NETCONF_GET_CONFIG_PATH = toPath(NETCONF_GET_CONFIG_QNAME);
    public static final @NonNull QName NETCONF_DISCARD_CHANGES_QNAME = QName.create(NETCONF_QNAME, "discard-changes");
    public static final @NonNull Absolute NETCONF_DISCARD_CHANGES_PATH = toPath(NETCONF_DISCARD_CHANGES_QNAME);
    public static final @NonNull QName NETCONF_TYPE_QNAME = QName.create(NETCONF_QNAME, "type").intern();
    public static final @NonNull QName NETCONF_FILTER_QNAME = QName.create(NETCONF_QNAME, "filter").intern();
    public static final @NonNull QName NETCONF_GET_QNAME =
        QName.create(NETCONF_QNAME, XmlNetconfConstants.GET).intern();
    public static final @NonNull NodeIdentifier NETCONF_GET_NODEID = NodeIdentifier.create(NETCONF_GET_QNAME);
    public static final @NonNull Absolute NETCONF_GET_PATH = toPath(NETCONF_GET_QNAME);
    public static final @NonNull QName NETCONF_RPC_QNAME =
        QName.create(NETCONF_QNAME, XmlNetconfConstants.RPC_KEY).intern();

    public static final @NonNull QName NETCONF_LOCK_QNAME = QName.create(NETCONF_QNAME, "lock").intern();
    public static final @NonNull NodeIdentifier NETCONF_LOCK_NODEID = NodeIdentifier.create(NETCONF_LOCK_QNAME);
    public static final @NonNull Absolute NETCONF_LOCK_PATH = toPath(NETCONF_LOCK_QNAME);
    public static final @NonNull QName NETCONF_UNLOCK_QNAME = QName.create(NETCONF_QNAME, "unlock").intern();
    public static final @NonNull NodeIdentifier NETCONF_UNLOCK_NODEID = NodeIdentifier.create(NETCONF_UNLOCK_QNAME);
    public static final @NonNull Absolute NETCONF_UNLOCK_PATH = toPath(NETCONF_UNLOCK_QNAME);

    public static final @NonNull NodeIdentifier EDIT_CONTENT_NODEID = NodeIdentifier.create(EditContent.QNAME);

    // Discard changes message
    public static final @NonNull ContainerNode DISCARD_CHANGES_RPC_CONTENT = Builders.containerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(NETCONF_DISCARD_CHANGES_QNAME)).build();

    // Commit changes message
    public static final @NonNull ContainerNode COMMIT_RPC_CONTENT = Builders.containerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(NETCONF_COMMIT_QNAME)).build();

    // Get message
    public static final @NonNull ContainerNode GET_RPC_CONTENT = Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_GET_NODEID).build();

    // Create-subscription changes message
    public static final @NonNull ContainerNode CREATE_SUBSCRIPTION_RPC_CONTENT = Builders.containerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(CREATE_SUBSCRIPTION_RPC_QNAME)).build();

    public static final @NonNull Absolute CREATE_SUBSCRIPTION_RPC_PATH = toPath(CREATE_SUBSCRIPTION_RPC_QNAME);

    public static final @NonNull NodeIdentifier NETCONF_FILTER_NODEID = NodeIdentifier.create(NETCONF_FILTER_QNAME);

    public static final @NonNull AnyxmlNode<?> EMPTY_FILTER = buildFilterStructure(getNetconfFilterElement());

    private NetconfMessageTransformUtil() {
        // Hidden on purpose
    }

    /**
     * Creation of the subtree filter structure using {@link YangInstanceIdentifier} path.
     *
     * @param identifier parent path / query
     * @param ctx        mountpoint schema context
     * @return created DOM structure with subtree filter
     */
    public static AnyxmlNode<?> toFilterStructure(final YangInstanceIdentifier identifier,
                                                       final EffectiveModelContext ctx) {
        final Element element = getNetconfFilterElement();
        try {
            NormalizedDataUtil.writeFilter(identifier, new DOMResult(element), ctx, null);
        } catch (IOException | XMLStreamException e) {
            throw new IllegalStateException("Unable to serialize filter element for path " + identifier, e);
        }
        return buildFilterStructure(element);
    }

    /**
     * Creation of the subtree filter structure using list of parent {@link YangInstanceIdentifier}
     * and specific selection fields. Field paths are relative to parent query path.
     *
     * @param fieldsFilters list of: parent path and selection fields
     * @param ctx           mountpoint schema context
     * @return created DOM structure with subtree filter
     */
    public static AnyxmlNode<?> toFilterStructure(final List<FieldsFilter> fieldsFilters,
                                                  final EffectiveModelContext ctx) {
        Preconditions.checkState(!fieldsFilters.isEmpty(), "An empty list of subtree filters is not allowed");
        final Element element = getNetconfFilterElement();

        for (final FieldsFilter filter : fieldsFilters) {
            try {
                NormalizedDataUtil.writeFilter(filter.path(), new DOMResult(element), ctx, null, filter.fields());
            } catch (IOException | XMLStreamException e) {
                throw new IllegalStateException(String.format(
                        "Unable to serialize filter element for path %s with fields: %s",
                        filter.path(), filter.fields()), e);
            }
        }
        return buildFilterStructure(element);
    }

    private static Element getNetconfFilterElement() {
        final Element element = XmlUtil.createElement(BLANK_DOCUMENT, NETCONF_FILTER_QNAME.getLocalName(),
                Optional.of(NETCONF_FILTER_QNAME.getNamespace().toString()));
        element.setAttributeNS(NETCONF_FILTER_QNAME.getNamespace().toString(), NETCONF_TYPE_QNAME.getLocalName(),
                SUBTREE);
        return element;
    }

    private static AnyxmlNode<?> buildFilterStructure(final Element element) {
        return Builders.anyXmlBuilder()
                .withNodeIdentifier(NETCONF_FILTER_NODEID)
                .withValue(new DOMSource(element))
                .build();
    }

    public static NodeIdentifier toId(final PathArgument arg) {
        return arg instanceof NodeIdentifier nodeId ? nodeId : toId(arg.getNodeType());
    }

    public static NodeIdentifier toId(final QName nodeType) {
        return new NodeIdentifier(nodeType);
    }

    public static Element getDataSubtree(final Document doc) {
        return (Element) doc.getElementsByTagNameNS(NETCONF_URI.toString(), "data").item(0);
    }

    public static boolean isDataRetrievalOperation(final QName rpc) {
        return NETCONF_URI.equals(rpc.getNamespace())
                && (NETCONF_GET_CONFIG_QNAME.getLocalName().equals(rpc.getLocalName())
                || NETCONF_GET_QNAME.getLocalName().equals(rpc.getLocalName()));
    }

    @Deprecated
    public static @NonNull ContainerNode wrap(final QName name, final DataContainerChild... node) {
        return wrap(toId(name), node);
    }

    public static @NonNull ContainerNode wrap(final NodeIdentifier name, final DataContainerChild... node) {
        return Builders.containerBuilder().withNodeIdentifier(name).withValue(ImmutableList.copyOf(node)).build();
    }

    /**
     * Create edit-config structure to invoke {@code operation} with {@code lastChildOverride} data on {@code dataPath}.
     *
     * @param ctx {@link EffectiveModelContext} device's model context
     * @param dataPath {@link YangInstanceIdentifier} path to data in device's data-store
     * @param operation Optional of {@link EffectiveOperation} action to be invoked
     * @param lastChildOverride Optional of {@code NormalizedNode} data on which action will be invoked
     * @return {@link DOMSourceAnyxmlNode} containing edit-config structure
     */
    public static DOMSourceAnyxmlNode createEditConfigAnyxml(
            final EffectiveModelContext ctx, final YangInstanceIdentifier dataPath,
            final Optional<EffectiveOperation> operation, final Optional<NormalizedNode> lastChildOverride) {
        if (dataPath.isEmpty()) {
            final var override = lastChildOverride.orElseThrow(() -> new IllegalArgumentException(
                "Data has to be present when creating structure for top level element"));
            Preconditions.checkArgument(override instanceof DataContainerChild,
                "Data has to be either container or a list node when creating structure for top level element, "
                    + "but was: %s", override);
        }

        final var element = XmlUtil.createElement(BLANK_DOCUMENT, NETCONF_CONFIG_QNAME.getLocalName(),
                Optional.of(NETCONF_CONFIG_QNAME.getNamespace().toString()));
        final var metadata = operation.map(o -> leafMetadata(dataPath, o)).orElse(null);
        try {
            if (lastChildOverride.isPresent()) {
                // TODO do not transform this into result and then to xml, rework the whole pipeline to directly write
                // into xml

                final var parentPath = dataPath.isEmpty() ? dataPath : dataPath.coerceParent();
                var result = new NormalizationResultHolder();
                try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(result)) {
                    try (var iidWriter = YangInstanceIdentifierWriter.open(streamWriter, ctx, parentPath);
                         var nnWriter = NormalizedNodeWriter.forStreamWriter(streamWriter)) {
                        nnWriter.write(lastChildOverride.orElseThrow());
                    }
                }
                NormalizedDataUtil.writeNormalizedNode(result.getResult().data(), metadata, new DOMResult(element), ctx,
                    null);
            } else {
                NormalizedDataUtil.writeNormalizedNode(dataPath, metadata, new DOMResult(element), ctx, null);
            }
        } catch (final IOException | XMLStreamException e) {
            throw new IllegalStateException("Unable to serialize edit config content element for path " + dataPath, e);
        }

        return Builders.anyXmlBuilder().withNodeIdentifier(NETCONF_CONFIG_NODEID).withValue(new DOMSource(element))
                .build();
    }

    private static NormalizedMetadata leafMetadata(final YangInstanceIdentifier path, final EffectiveOperation oper) {
        record BuilderAndArg(ImmutableNormalizedMetadata.Builder builder, PathArgument arg) {
            BuilderAndArg {
                requireNonNull(builder);
                requireNonNull(arg);
            }
        }

        final var args = path.getPathArguments();
        final var builders = new ArrayDeque<BuilderAndArg>(args.size());

        // Step one: open builders
        for (PathArgument arg : args) {
            builders.push(new BuilderAndArg(ImmutableNormalizedMetadata.builder(), arg));
        }

        // Step two: set the top builder's metadata
        builders.peek().builder.withAnnotation(NETCONF_OPERATION_QNAME_LEGACY, oper.xmlValue());

        // Step three: build the tree
        while (true) {
            final var current = builders.pop();
            final var currentMeta = current.builder.build();
            final var parent = builders.peek();
            if (parent != null) {
                parent.builder.withChild(current.arg, currentMeta);
            } else {
                return currentMeta;
            }
        }
    }

    public static DataContainerChild createEditConfigStructure(final EffectiveModelContext ctx,
            final YangInstanceIdentifier dataPath, final Optional<EffectiveOperation> operation,
            final Optional<NormalizedNode> lastChildOverride) {
        return Builders.choiceBuilder().withNodeIdentifier(EDIT_CONTENT_NODEID)
                .withChild(createEditConfigAnyxml(ctx, dataPath, operation, lastChildOverride)).build();
    }

    public static @NonNull Absolute toPath(final QName rpc) {
        return Absolute.of(rpc);
    }

    public static Map.Entry<Instant, XmlElement> stripNotification(final NetconfMessage message) {
        final XmlElement xmlElement = XmlElement.fromDomDocument(message.getDocument());
        final List<XmlElement> childElements = xmlElement.getChildElements();
        Preconditions.checkArgument(childElements.size() == 2, "Unable to parse notification %s, unexpected format."
                + "\nExpected 2 childElements, actual childElements size is %s", message, childElements.size());

        final XmlElement eventTimeElement;
        final XmlElement notificationElement;

        if (childElements.get(0).getName().equals(XmlNetconfConstants.EVENT_TIME)) {
            eventTimeElement = childElements.get(0);
            notificationElement = childElements.get(1);
        } else if (childElements.get(1).getName().equals(XmlNetconfConstants.EVENT_TIME)) {
            eventTimeElement = childElements.get(1);
            notificationElement = childElements.get(0);
        } else {
            throw new IllegalArgumentException(
                "Notification payload does not contain " + XmlNetconfConstants.EVENT_TIME + " " + message);
        }

        try {
            return Map.entry(
                    NotificationMessage.RFC3339_DATE_PARSER.apply(eventTimeElement.getTextContent()),
                    notificationElement);
        } catch (final DocumentedException e) {
            throw new IllegalArgumentException(
                "Notification payload does not contain " + XmlNetconfConstants.EVENT_TIME + " " + message, e);
        } catch (final DateTimeParseException e) {
            LOG.warn("Unable to parse event time from {}. Setting time to {}", eventTimeElement,
                NotificationMessage.UNKNOWN_EVENT_TIME, e);
            return Map.entry(NotificationMessage.UNKNOWN_EVENT_TIME, notificationElement);
        }
    }

    public static DOMResult prepareDomResultForRpcRequest(final QName rpcQName, final MessageCounter counter) {
        final Document document = XmlUtil.newDocument();
        final Element rpcNS =
                document.createElementNS(NETCONF_RPC_QNAME.getNamespace().toString(), NETCONF_RPC_QNAME.getLocalName());
        // set msg id
        rpcNS.setAttribute(XmlNetconfConstants.MESSAGE_ID, counter.getNewMessageId(MESSAGE_ID_PREFIX));
        final Element elementNS = document.createElementNS(rpcQName.getNamespace().toString(), rpcQName.getLocalName());
        rpcNS.appendChild(elementNS);
        document.appendChild(rpcNS);
        return new DOMResult(elementNS);
    }

    public static DOMResult prepareDomResultForActionRequest(final DataSchemaContextTree dataSchemaContextTree,
            final DOMDataTreeIdentifier domDataTreeIdentifier, final MessageCounter counter, final QName action) {
        final Document document = XmlUtil.newDocument();
        final Element rpcNS =
                document.createElementNS(NETCONF_RPC_QNAME.getNamespace().toString(), NETCONF_RPC_QNAME.getLocalName());
        // set msg id
        rpcNS.setAttribute(XmlNetconfConstants.MESSAGE_ID, counter.getNewMessageId(MESSAGE_ID_PREFIX));

        final Element actionNS = document.createElementNS(YangConstants.RFC6020_YANG_NAMESPACE_STRING, "action");
        final DataSchemaContext rootSchemaContextNode = dataSchemaContextTree.getRoot();
        final Element actionData = prepareActionData(rootSchemaContextNode, actionNS,
                domDataTreeIdentifier.getRootIdentifier().getPathArguments().iterator(), document);

        final Element specificActionElement =
                document.createElementNS(action.getNamespace().toString(), action.getLocalName());
        actionData.appendChild(specificActionElement);
        rpcNS.appendChild(actionNS);
        document.appendChild(rpcNS);
        return new DOMResult(specificActionElement);
    }

    private static Element prepareActionData(final DataSchemaContext currentParentSchemaNode,
            final Element actionNS, final Iterator<PathArgument> iterator, final Document document) {
        if (iterator.hasNext()) {
            final PathArgument next = iterator.next();

            final DataSchemaContext current = currentParentSchemaNode instanceof DataSchemaContext.Composite composite
                ? composite.childByArg(next) : null;
            Preconditions.checkArgument(current != null, "Invalid input: schema for argument %s not found", next);

            if (current instanceof PathMixin) {
                return prepareActionData(current, actionNS, iterator, document);
            }

            final QName actualNS = next.getNodeType();
            final Element actualElement = document.createElementNS(actualNS.getNamespace().toString(),
                    actualNS.getLocalName());
            if (next instanceof NodeWithValue) {
                actualElement.setNodeValue(((NodeWithValue<?>) next).getValue().toString());
            } else if (next instanceof NodeIdentifierWithPredicates) {
                for (Entry<QName, Object> entry : ((NodeIdentifierWithPredicates) next).entrySet()) {
                    final Element entryElement = document.createElementNS(entry.getKey().getNamespace().toString(),
                            entry.getKey().getLocalName());
                    entryElement.setTextContent(entry.getValue().toString());
                    entryElement.setNodeValue(entry.getValue().toString());
                    actualElement.appendChild(entryElement);
                }
            }
            actionNS.appendChild(actualElement);
            return prepareActionData(current, actualElement, iterator, document);
        } else {
            return actionNS;
        }
    }

    public static void writeNormalizedOperationInput(final ContainerNode normalized, final DOMResult result,
            final Absolute operationPath, final EffectiveModelContext baseNetconfCtx)
                throws IOException, XMLStreamException {
        final var stack = SchemaInferenceStack.of(baseNetconfCtx, operationPath);
        stack.enterSchemaTree(YangConstants.operationInputQName(operationPath.lastNodeIdentifier().getModule()));
        final var inputInference = stack.toSchemaTreeInference();

        final XMLStreamWriter writer = NormalizedDataUtil.XML_FACTORY.createXMLStreamWriter(result);
        try {
            try (NormalizedNodeStreamWriter normalizedNodeStreamWriter =
                    XMLStreamNormalizedNodeStreamWriter.create(writer, inputInference)) {
                try (SchemaOrderedNormalizedNodeWriter normalizedNodeWriter =
                        new SchemaOrderedNormalizedNodeWriter(normalizedNodeStreamWriter, inputInference)) {
                    final Collection<DataContainerChild> value = normalized.body();
                    normalizedNodeWriter.write(value);
                    normalizedNodeWriter.flush();
                }
            }
        } finally {
            try {
                writer.close();
            } catch (final XMLStreamException e) {
                LOG.warn("Unable to close resource properly", e);
            }
        }
    }
}

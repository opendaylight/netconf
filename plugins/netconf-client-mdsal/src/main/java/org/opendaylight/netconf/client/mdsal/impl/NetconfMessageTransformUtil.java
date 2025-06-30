/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.YangModuleInfoImpl.qnameOf;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.messages.RpcMessage;
import org.opendaylight.netconf.api.xml.XMLSupport;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps.ConfigNodeKey;
import org.opendaylight.netconf.client.mdsal.util.NormalizedDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Candidate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.CommitInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.CopyConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.DiscardChangesInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Lock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Unlock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Validate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.edit.config.input.EditContent;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.config.output.Data;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.input.Filter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.yang.common.QName;
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
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.YangInstanceIdentifierWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedMetadata;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaOrderedNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
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

    // Blank document used for creation of new DOM nodes
    private static final Document BLANK_DOCUMENT = XmlUtil.newDocument();

    public static final @NonNull QName IETF_NETCONF_MONITORING =
            QName.create(NetconfState.QNAME, "ietf-netconf-monitoring").intern();

    public static final @NonNull NodeIdentifier NETCONF_DATA_NODEID = NodeIdentifier.create(Data.QNAME);

    public static final @NonNull NodeIdentifier NETCONF_ERROR_OPTION_NODEID =
        NodeIdentifier.create(qnameOf("error-option"));
    public static final @NonNull NodeIdentifier NETCONF_RUNNING_NODEID = NodeIdentifier.create(qnameOf("running"));
    public static final @NonNull NodeIdentifier NETCONF_SOURCE_NODEID = NodeIdentifier.create(qnameOf("source"));
    public static final @NonNull NodeIdentifier NETCONF_CANDIDATE_NODEID = NodeIdentifier.create(Candidate.QNAME);
    public static final @NonNull NodeIdentifier NETCONF_TARGET_NODEID = NodeIdentifier.create(qnameOf("target"));
    public static final @NonNull NodeIdentifier NETCONF_CONFIG_NODEID = NodeIdentifier.create(qnameOf("config"));
    public static final @NonNull NodeIdentifier NETCONF_OUTPUT_NODEID = NodeIdentifier.create(qnameOf("output"));

    public static final @NonNull NodeIdentifier NETCONF_VALIDATE_NODEID = NodeIdentifier.create(Validate.QNAME);
    public static final @NonNull NodeIdentifier NETCONF_COPY_CONFIG_NODEID = NodeIdentifier.create(CopyConfig.QNAME);

    private static final @NonNull QName NETCONF_OPERATION_QNAME_LEGACY =
        QName.create(NamespaceURN.BASE, XmlNetconfConstants.OPERATION_ATTR_KEY).intern();

    public static final @NonNull NodeIdentifier NETCONF_DEFAULT_OPERATION_NODEID =
        NodeIdentifier.create(qnameOf("default-operation"));
    public static final @NonNull NodeIdentifier NETCONF_EDIT_CONFIG_NODEID = NodeIdentifier.create(EditConfig.QNAME);
    public static final @NonNull NodeIdentifier NETCONF_GET_CONFIG_NODEID = NodeIdentifier.create(GetConfig.QNAME);
    public static final @NonNull NodeIdentifier NETCONF_GET_NODEID = NodeIdentifier.create(Get.QNAME);
    public static final @NonNull QName NETCONF_RPC_QNAME = qnameOf(RpcMessage.ELEMENT_NAME);

    public static final @NonNull NodeIdentifier NETCONF_LOCK_NODEID = NodeIdentifier.create(Lock.QNAME);
    public static final @NonNull NodeIdentifier NETCONF_UNLOCK_NODEID = NodeIdentifier.create(Unlock.QNAME);
    public static final @NonNull NodeIdentifier EDIT_CONTENT_NODEID = NodeIdentifier.create(EditContent.QNAME);

    // Discard changes message
    public static final @NonNull ContainerNode DISCARD_CHANGES_RPC_CONTENT = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(DiscardChangesInput.QNAME)).build();

    // Commit changes message
    public static final @NonNull ContainerNode COMMIT_RPC_CONTENT = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(CommitInput.QNAME)).build();

    // Get message
    public static final @NonNull ContainerNode GET_RPC_CONTENT = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NETCONF_GET_NODEID).build();

    public static final @NonNull AnyxmlNode<?> EMPTY_FILTER = buildFilterStructure(newFilterElement());

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
        final Element element = newFilterElement();
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
        final Element element = newFilterElement();

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

    private static Element newFilterElement() {
        final var element = BLANK_DOCUMENT.createElementNS(NamespaceURN.BASE, "filter");
        element.setAttribute("type", "subtree");
        return element;
    }

    private static AnyxmlNode<?> buildFilterStructure(final Element element) {
        return ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
            .withNodeIdentifier(new NodeIdentifier(Filter.QNAME))
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
        return (Element) doc.getElementsByTagNameNS(NamespaceURN.BASE, "data").item(0);
    }

    public static boolean isDataRetrievalOperation(final QName rpc) {
        return YangConstants.NETCONF_NAMESPACE.equals(rpc.getNamespace())
            && (GetConfig.QNAME.getLocalName().equals(rpc.getLocalName())
                || Get.QNAME.getLocalName().equals(rpc.getLocalName()));
    }

    @Deprecated
    public static @NonNull ContainerNode wrap(final QName name, final DataContainerChild... node) {
        return wrap(toId(name), node);
    }

    public static @NonNull ContainerNode wrap(final NodeIdentifier name, final DataContainerChild... node) {
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(name)
            .withValue(ImmutableList.copyOf(node))
            .build();
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
    public static AnyxmlNode<DOMSource> createEditConfigAnyxml(final EffectiveModelContext ctx,
            final YangInstanceIdentifier dataPath, final Optional<EffectiveOperation> operation,
            final Optional<NormalizedNode> lastChildOverride) {
        if (dataPath.isEmpty()) {
            final var override = lastChildOverride.orElseThrow(() -> new IllegalArgumentException(
                "Data has to be present when creating structure for top level element"));
            Preconditions.checkArgument(override instanceof DataContainerChild,
                "Data has to be either container or a list node when creating structure for top level element, "
                    + "but was: %s", override);
        }

        final var element = BLANK_DOCUMENT.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.CONFIG_KEY);
        final var metadata = operation.map(o -> leafMetadata(dataPath, o)).orElse(null);
        writeDataIntoElement(ctx, dataPath, lastChildOverride, metadata, element);

        return ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
            .withNodeIdentifier(NETCONF_CONFIG_NODEID)
            .withValue(new DOMSource(element))
            .build();
    }

    /**
     * Create edit-config structure to invoke {@code operation} with {@code lastChildOverride} data on {@code dataPath}.
     *
     * @param elements {@link EffectiveModelContext} device's model context
     * @return {@link DOMSourceAnyxmlNode} containing edit-config structure
     */
    public static AnyxmlNode<DOMSource> createEditConfigAnyxml(final EffectiveModelContext ctx,
            final Map<ConfigNodeKey, Optional<NormalizedNode>> elements) {
        final var element = BLANK_DOCUMENT.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.CONFIG_KEY);
        for (final var nodeEntry : elements.entrySet()) {
            final var dataPath = nodeEntry.getKey().identifier();
            final var optNode = nodeEntry.getValue();
            if (dataPath.isEmpty()) {
                final var override = optNode.orElseThrow(() -> new IllegalArgumentException(
                    "Data has to be present when creating structure for top level element"));
                Preconditions.checkArgument(override instanceof DataContainerChild,
                    "Data has to be either container or a list node when creating structure for top level element, "
                        + "but was: %s", override);
            }
            final var operation = nodeEntry.getKey().operation();
            final var metadata = operation == null ? null : leafMetadata(dataPath, operation);
            writeDataIntoElement(ctx, dataPath, optNode, metadata, element);
        }

        return ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
            .withNodeIdentifier(NETCONF_CONFIG_NODEID)
            .withValue(new DOMSource(element))
            .build();
    }

    private static void writeDataIntoElement(final EffectiveModelContext ctx, final YangInstanceIdentifier dataPath,
            final Optional<NormalizedNode> lastChildOverride, final NormalizedMetadata metadata,
            final Element element) {
        try {
            if (lastChildOverride.isPresent()) {
                // TODO do not transform this into result and then to xml, rework the whole pipeline to directly write
                // into xml
                final var parentPath = dataPath.isEmpty() ? dataPath : dataPath.coerceParent();
                final var result = new NormalizationResultHolder();
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
        return ImmutableNodes.newChoiceBuilder()
            .withNodeIdentifier(EDIT_CONTENT_NODEID)
            .withChild(createEditConfigAnyxml(ctx, dataPath, operation, lastChildOverride))
            .build();
    }

    public static @NonNull Absolute toPath(final QName rpc) {
        return Absolute.of(rpc);
    }

    public static Map.Entry<Instant, XmlElement> stripNotification(final NetconfMessage message) {
        final var xmlElement = XmlElement.fromDomDocument(message.getDocument());
        final var childElements = xmlElement.getChildElements();
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
        final var document = XmlUtil.newDocument();
        final var rpcNS =
                document.createElementNS(NETCONF_RPC_QNAME.getNamespace().toString(), NETCONF_RPC_QNAME.getLocalName());
        // set msg id
        rpcNS.setAttribute(XmlNetconfConstants.MESSAGE_ID, counter.getNewMessageId(MESSAGE_ID_PREFIX));
        final var elementNS = document.createElementNS(rpcQName.getNamespace().toString(), rpcQName.getLocalName());
        rpcNS.appendChild(elementNS);
        document.appendChild(rpcNS);
        return new DOMResult(elementNS);
    }

    public static DOMResult prepareDomResultForActionRequest(final DataSchemaContextTree dataSchemaContextTree,
            final DOMDataTreeIdentifier domDataTreeIdentifier, final MessageCounter counter, final QName action) {
        final var document = XmlUtil.newDocument();
        final var rpcNS =
                document.createElementNS(NETCONF_RPC_QNAME.getNamespace().toString(), NETCONF_RPC_QNAME.getLocalName());
        // set msg id
        rpcNS.setAttribute(XmlNetconfConstants.MESSAGE_ID, counter.getNewMessageId(MESSAGE_ID_PREFIX));

        final var actionNS = document.createElementNS(YangConstants.RFC6020_YANG_NAMESPACE_STRING, "action");
        final var rootSchemaContextNode = dataSchemaContextTree.getRoot();
        final var actionData = prepareActionData(rootSchemaContextNode, actionNS,
                domDataTreeIdentifier.path().getPathArguments().iterator(), document);

        final var specificActionElement =
                document.createElementNS(action.getNamespace().toString(), action.getLocalName());
        actionData.appendChild(specificActionElement);
        rpcNS.appendChild(actionNS);
        document.appendChild(rpcNS);
        return new DOMResult(specificActionElement);
    }

    private static Element prepareActionData(final DataSchemaContext currentParentSchemaNode,
            final Element actionNS, final Iterator<PathArgument> iterator, final Document document) {
        if (iterator.hasNext()) {
            final var next = iterator.next();

            final var current = currentParentSchemaNode instanceof DataSchemaContext.Composite composite
                ? composite.childByArg(next) : null;
            if (current == null) {
                throw new IllegalArgumentException("Invalid input: schema for argument %s not found".formatted(next));
            }

            if (current instanceof PathMixin) {
                return prepareActionData(current, actionNS, iterator, document);
            }

            final var actualNS = next.getNodeType();
            final var actualElement = document.createElementNS(actualNS.getNamespace().toString(),
                    actualNS.getLocalName());
            if (next instanceof NodeWithValue<?> withValue) {
                actualElement.setNodeValue(withValue.getValue().toString());
            } else if (next instanceof NodeIdentifierWithPredicates nip) {
                for (var entry : nip.entrySet()) {
                    final var qname = entry.getKey();
                    final var entryElement = document.createElementNS(qname.getNamespace().toString(),
                            qname.getLocalName());
                    final var value = entry.getValue().toString();
                    entryElement.setTextContent(value);
                    entryElement.setNodeValue(value);
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

        final var xmlWriter = XMLSupport.newStreamWriter(result);
        try {
            try (var streamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, inputInference);
                 var nnWriter = new SchemaOrderedNormalizedNodeWriter(streamWriter, inputInference)) {
                nnWriter.write(normalized.body());
                nnWriter.flush();
            }
        } finally {
            try {
                xmlWriter.close();
            } catch (final XMLStreamException e) {
                LOG.warn("Unable to close resource properly", e);
            }
        }
    }
}

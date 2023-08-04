/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.IETF_NETCONF_MONITORING;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_DATA_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_FILTER_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_FILTER_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_TYPE_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.toId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.common.mdsal.NormalizedDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;
import org.xml.sax.SAXException;

/**
 * Holds QNames for all yang modules reported by ietf-netconf-monitoring/state/schemas.
 */
public final class NetconfStateSchemas implements NetconfDeviceSchemas {
    public static final NetconfStateSchemas EMPTY = new NetconfStateSchemas(ImmutableSet.of());

    private static final Logger LOG = LoggerFactory.getLogger(NetconfStateSchemas.class);
    private static final YangInstanceIdentifier STATE_SCHEMAS_IDENTIFIER = YangInstanceIdentifier.builder()
        .node(NetconfState.QNAME).node(Schemas.QNAME).build();
    private static final String MONITORING_NAMESPACE = IETF_NETCONF_MONITORING.getNamespace().toString();
    private static final @NonNull ContainerNode GET_SCHEMAS_RPC;

    static {
        final Document document = XmlUtil.newDocument();

        final Element filterElem = XmlUtil.createElement(document, NETCONF_FILTER_QNAME.getLocalName(),
            Optional.of(NETCONF_FILTER_QNAME.getNamespace().toString()));
        filterElem.setAttributeNS(NETCONF_FILTER_QNAME.getNamespace().toString(), NETCONF_TYPE_QNAME.getLocalName(),
            "subtree");

        final Element stateElem = XmlUtil.createElement(document, NetconfState.QNAME.getLocalName(),
            Optional.of(NetconfState.QNAME.getNamespace().toString()));
        stateElem.appendChild(XmlUtil.createElement(document, Schemas.QNAME.getLocalName(),
            Optional.of(Schemas.QNAME.getNamespace().toString())));
        filterElem.appendChild(stateElem);

        GET_SCHEMAS_RPC = Builders.containerBuilder()
                .withNodeIdentifier(NETCONF_GET_NODEID)
                .withChild(Builders.anyXmlBuilder()
                    .withNodeIdentifier(NETCONF_FILTER_NODEID)
                    .withValue(new DOMSource(filterElem))
                    .build())
                .build();
    }

    private final Set<RemoteYangSchema> availableYangSchemas;

    public NetconfStateSchemas(final Set<RemoteYangSchema> availableYangSchemas) {
        this.availableYangSchemas = availableYangSchemas;
    }

    public Set<RemoteYangSchema> getAvailableYangSchemas() {
        return availableYangSchemas;
    }

    @Override
    public Set<QName> getAvailableYangSchemasQNames() {
        return availableYangSchemas.stream()
            .map(RemoteYangSchema::getQName)
            .collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Issue get request to remote device and parse response to find all schemas under netconf-state/schemas.
     */
    static NetconfStateSchemas create(final DOMRpcService deviceRpc,
            final NetconfSessionPreferences remoteSessionCapabilities, final RemoteDeviceId id,
            final EffectiveModelContext schemaContext) {
        if (!remoteSessionCapabilities.isMonitoringSupported()) {
            // TODO - need to search for get-schema support, not just ietf-netconf-monitoring support
            // issue might be a deviation to ietf-netconf-monitoring where get-schema is unsupported...
            LOG.warn("{}: Netconf monitoring not supported on device, cannot detect provided schemas", id);
            return EMPTY;
        }

        final DOMRpcResult schemasNodeResult;
        try {
            schemasNodeResult = deviceRpc.invokeRpc(NETCONF_GET_QNAME, GET_SCHEMAS_RPC).get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(id
                    + ": Interrupted while waiting for response to " + STATE_SCHEMAS_IDENTIFIER, e);
        } catch (final ExecutionException e) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed", id, STATE_SCHEMAS_IDENTIFIER, e);
            return EMPTY;
        }

        if (!schemasNodeResult.errors().isEmpty()) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed, {}",
                    id, STATE_SCHEMAS_IDENTIFIER, schemasNodeResult.errors());
            return EMPTY;
        }

        final Optional<? extends NormalizedNode> optSchemasNode = findSchemasNode(schemasNodeResult.value(),
                schemaContext);
        if (optSchemasNode.isEmpty()) {
            LOG.warn("{}: Unable to detect available schemas, get to {} was empty", id, STATE_SCHEMAS_IDENTIFIER);
            return EMPTY;
        }

        final NormalizedNode schemasNode = optSchemasNode.orElseThrow();
        checkState(schemasNode instanceof ContainerNode, "Expecting container containing schemas, but was %s",
            schemasNode);
        return create(id, (ContainerNode) schemasNode);
    }

    /**
     * Parse response of get(netconf-state/schemas) to find all schemas under netconf-state/schemas.
     */
    @VisibleForTesting
    protected static NetconfStateSchemas create(final RemoteDeviceId id, final ContainerNode schemasNode) {
        final Set<RemoteYangSchema> availableYangSchemas = new HashSet<>();

        final DataContainerChild child = schemasNode.childByArg(toId(Schema.QNAME));
        checkState(child != null, "Unable to find list: %s in response: %s", Schema.QNAME.withoutRevision(),
            schemasNode);
        checkState(child instanceof MapNode,
                "Unexpected structure for container: %s in response: %s. Expecting a list",
                Schema.QNAME.withoutRevision(), schemasNode);

        for (final MapEntryNode schemaNode : ((MapNode) child).body()) {
            final Optional<RemoteYangSchema> fromCompositeNode =
                    RemoteYangSchema.createFromNormalizedNode(id, schemaNode);
            fromCompositeNode.ifPresent(availableYangSchemas::add);
        }

        return new NetconfStateSchemas(availableYangSchemas);
    }

    private static Optional<? extends NormalizedNode> findSchemasNode(final NormalizedNode result,
            final EffectiveModelContext schemaContext) {
        if (result == null) {
            return Optional.empty();
        }
        // FIXME: unchecked cast
        final var rpcResult = ((ContainerNode) result).childByArg(NETCONF_DATA_NODEID);
        if (rpcResult == null) {
            return Optional.empty();
        }

        verify(rpcResult instanceof DOMSourceAnyxmlNode, "Unexpected result %s", rpcResult);

        // Server may include additional data which we do not understand. Make sure we trim the input before we try
        // to interpret it.
        // FIXME: this is something NetconfUtil.transformDOMSourceToNormalizedNode(), and more generally, NormalizedNode
        //        codecs should handle. We really want to a NormalizedNode tree which can be directly queried for known
        //        things while completely ignoring XML content (and hence its semantics) of other elements.
        final var filteredBody = ietfMonitoringCopy(((DOMSourceAnyxmlNode) rpcResult).body());

        final NormalizedNode dataNode;
        try {
            dataNode = NormalizedDataUtil.transformDOMSourceToNormalizedNode(schemaContext, filteredBody).getResult()
                .data();
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            LOG.warn("Failed to transform {}", rpcResult, e);
            return Optional.empty();
        }

        // FIXME: unchecked cast
        final var nStateNode = ((DataContainerNode) dataNode).childByArg(toId(NetconfState.QNAME));
        if (nStateNode == null) {
            return Optional.empty();
        }

        // FIXME: unchecked cast
        return ((DataContainerNode) nStateNode).findChildByArg(toId(Schemas.QNAME));
    }

    @VisibleForTesting
    static DOMSource ietfMonitoringCopy(final DOMSource domSource) {
        final var sourceDoc = XmlUtil.newDocument();
        sourceDoc.appendChild(sourceDoc.importNode(domSource.getNode(), true));

        final var treeWalker = ((DocumentTraversal) sourceDoc).createTreeWalker(sourceDoc.getDocumentElement(),
            NodeFilter.SHOW_ALL, node -> {
                final var namespace = node.getNamespaceURI();
                return namespace == null || MONITORING_NAMESPACE.equals(namespace)
                    ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
            }, false);

        final var filteredDoc = XmlUtil.newDocument();
        filteredDoc.appendChild(filteredDoc.importNode(treeWalker.getRoot(), false));
        final var filteredElement = filteredDoc.getDocumentElement();
        copyChildren(treeWalker, filteredDoc, filteredElement);

        return new DOMSource(filteredElement);
    }

    private static void copyChildren(final TreeWalker walker, final Document targetDoc, final Node targetNode) {
        if (walker.firstChild() != null) {
            for (var node = walker.getCurrentNode(); node != null; node = walker.nextSibling()) {
                final var importedNode = targetDoc.importNode(node, false);
                targetNode.appendChild(importedNode);
                copyChildren(walker, targetDoc, importedNode);
                walker.setCurrentNode(node);
            }
        }
    }

    public static final class RemoteYangSchema {
        private final QName qname;

        RemoteYangSchema(final QName qname) {
            this.qname = qname;
        }

        public QName getQName() {
            return qname;
        }

        static Optional<RemoteYangSchema> createFromNormalizedNode(final RemoteDeviceId id,
                                                                   final MapEntryNode schemaNode) {
            final QName schemaNodeId = schemaNode.name().getNodeType();
            checkArgument(schemaNodeId.equals(Schema.QNAME), "Wrong QName %s", schemaNodeId);

            QName childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_FORMAT;

            final String formatAsString = getSingleChildNodeValue(schemaNode, childNode).orElseThrow();

            if (!formatAsString.equals(Yang.QNAME.toString())) {
                LOG.debug("{}: Ignoring schema due to unsupported format: {}", id, formatAsString);
                return Optional.empty();
            }

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_LOCATION;
            final Set<String> locationsAsString = getAllChildNodeValues(schemaNode, childNode);
            if (!locationsAsString.contains(Schema.Location.Enumeration.NETCONF.toString())) {
                LOG.debug("{}: Ignoring schema due to unsupported location: {}", id, locationsAsString);
                return Optional.empty();
            }

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_NAMESPACE;
            final Optional<String> namespaceValue = getSingleChildNodeValue(schemaNode, childNode);
            if (namespaceValue.isEmpty()) {
                LOG.warn("{}: Ignoring schema due to missing namespace", id);
                return Optional.empty();
            }
            final String namespaceAsString = namespaceValue.orElseThrow();

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_VERSION;
            // Revision does not have to be filled
            final Optional<String> revisionAsString = getSingleChildNodeValue(schemaNode, childNode);

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_IDENTIFIER;
            final String moduleNameAsString = getSingleChildNodeValue(schemaNode, childNode).orElseThrow();

            final QName moduleQName = revisionAsString.isPresent()
                    ? QName.create(namespaceAsString, revisionAsString.orElseThrow(), moduleNameAsString)
                    : QName.create(XMLNamespace.of(namespaceAsString), moduleNameAsString);

            return Optional.of(new RemoteYangSchema(moduleQName));
        }

        /**
         * Extracts all values of a leaf-list node as a set of strings.
         */
        private static Set<String> getAllChildNodeValues(final DataContainerNode schemaNode,
                                                         final QName childNodeQName) {
            final Set<String> extractedValues = new HashSet<>();
            final DataContainerChild child = schemaNode.childByArg(toId(childNodeQName));
            checkArgument(child != null, "Child nodes %s not present", childNodeQName);
            checkArgument(child instanceof LeafSetNode, "Child nodes %s not present", childNodeQName);
            for (final LeafSetEntryNode<?> childNode : ((LeafSetNode<?>) child).body()) {
                extractedValues.add(getValueOfSimpleNode(childNode).orElseThrow());
            }
            return extractedValues;
        }

        private static Optional<String> getSingleChildNodeValue(final DataContainerNode schemaNode,
                                                                final QName childNode) {
            final Optional<DataContainerChild> node = schemaNode.findChildByArg(toId(childNode));
            if (node.isPresent()) {
                return getValueOfSimpleNode(node.orElseThrow());
            }
            LOG.debug("Child node {} not present", childNode);
            return Optional.empty();
        }

        private static Optional<String> getValueOfSimpleNode(final NormalizedNode node) {
            final String valueStr = node.body().toString();
            return Strings.isNullOrEmpty(valueStr) ? Optional.empty() : Optional.of(valueStr.trim());
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            final RemoteYangSchema that = (RemoteYangSchema) obj;

            return qname.equals(that.qname);
        }

        @Override
        public int hashCode() {
            return qname.hashCode();
        }
    }
}

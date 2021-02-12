/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DATA_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_FILTER_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_FILTER_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_TYPE_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.net.URI;
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
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yangtools.yang.common.QName;
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
import org.xml.sax.SAXException;

/**
 * Holds QNames for all yang modules reported by ietf-netconf-monitoring/state/schemas.
 */
public final class NetconfStateSchemas implements NetconfDeviceSchemas {
    public static final NetconfStateSchemas EMPTY = new NetconfStateSchemas(ImmutableSet.of());

    private static final Logger LOG = LoggerFactory.getLogger(NetconfStateSchemas.class);
    private static final YangInstanceIdentifier STATE_SCHEMAS_IDENTIFIER =
            YangInstanceIdentifier.builder().node(NetconfState.QNAME).node(Schemas.QNAME).build();
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
        return getAvailableYangSchemas().stream().map(RemoteYangSchema::getQName)
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
            throw new RuntimeException(id
                    + ": Interrupted while waiting for response to " + STATE_SCHEMAS_IDENTIFIER, e);
        } catch (final ExecutionException e) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed", id, STATE_SCHEMAS_IDENTIFIER, e);
            return EMPTY;
        }

        if (!schemasNodeResult.getErrors().isEmpty()) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed, {}",
                    id, STATE_SCHEMAS_IDENTIFIER, schemasNodeResult.getErrors());
            return EMPTY;
        }

        final Optional<? extends NormalizedNode<?, ?>> optSchemasNode = findSchemasNode(schemasNodeResult.getResult(),
                schemaContext);
        if (!optSchemasNode.isPresent()) {
            LOG.warn("{}: Unable to detect available schemas, get to {} was empty", id, STATE_SCHEMAS_IDENTIFIER);
            return EMPTY;
        }

        final NormalizedNode<?, ?> schemasNode = optSchemasNode.get();
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

        final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> child =
                schemasNode.getChild(toId(Schema.QNAME));
        checkState(child.isPresent(), "Unable to find list: %s in response: %s", Schema.QNAME.withoutRevision(),
            schemasNode);
        checkState(child.get() instanceof MapNode,
                "Unexpected structure for container: %s in response: %s. Expecting a list",
                Schema.QNAME.withoutRevision(), schemasNode);

        for (final MapEntryNode schemaNode : ((MapNode) child.get()).getValue()) {
            final Optional<RemoteYangSchema> fromCompositeNode =
                    RemoteYangSchema.createFromNormalizedNode(id, schemaNode);
            if (fromCompositeNode.isPresent()) {
                availableYangSchemas.add(fromCompositeNode.get());
            }
        }

        return new NetconfStateSchemas(availableYangSchemas);
    }

    private static Optional<? extends NormalizedNode<?, ?>> findSchemasNode(final NormalizedNode<?, ?> result,
            final EffectiveModelContext schemaContext) {
        if (result == null) {
            return Optional.empty();
        }
        final Optional<DataContainerChild<?, ?>> rpcResultOpt = ((ContainerNode)result).getChild(NETCONF_DATA_NODEID);
        if (!rpcResultOpt.isPresent()) {
            return Optional.empty();
        }

        final DataContainerChild<?, ?> rpcResult = rpcResultOpt.get();
        verify(rpcResult instanceof DOMSourceAnyxmlNode, "Unexpected result %s", rpcResult);
        final NormalizedNode<?, ?> dataNode;

        try {
            dataNode = NetconfUtil.transformDOMSourceToNormalizedNode(schemaContext,
                    ((DOMSourceAnyxmlNode) rpcResult).getValue()).getResult();
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            LOG.warn("Failed to transform {}", rpcResult, e);
            return Optional.empty();
        }

        final Optional<DataContainerChild<?, ?>> nStateNode = ((DataContainerNode<?>) dataNode).getChild(
            toId(NetconfState.QNAME));
        if (!nStateNode.isPresent()) {
            return Optional.empty();
        }

        return ((DataContainerNode<?>) nStateNode.get()).getChild(toId(Schemas.QNAME));
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
            checkArgument(schemaNode.getNodeType().equals(Schema.QNAME), "Wrong QName %s", schemaNode.getNodeType());

            QName childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_FORMAT;

            final String formatAsString = getSingleChildNodeValue(schemaNode, childNode).get();

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
            if (!namespaceValue.isPresent()) {
                LOG.warn("{}: Ignoring schema due to missing namespace", id);
                return Optional.empty();
            }
            final String namespaceAsString = namespaceValue.get();

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_VERSION;
            // Revision does not have to be filled
            final Optional<String> revisionAsString = getSingleChildNodeValue(schemaNode, childNode);

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_IDENTIFIER;
            final String moduleNameAsString = getSingleChildNodeValue(schemaNode, childNode).get();

            final QName moduleQName = revisionAsString.isPresent()
                    ? QName.create(namespaceAsString, revisionAsString.get(), moduleNameAsString)
                    : QName.create(URI.create(namespaceAsString), moduleNameAsString);

            return Optional.of(new RemoteYangSchema(moduleQName));
        }

        /**
         * Extracts all values of a leaf-list node as a set of strings.
         */
        private static Set<String> getAllChildNodeValues(final DataContainerNode<?> schemaNode,
                                                         final QName childNodeQName) {
            final Set<String> extractedValues = new HashSet<>();
            final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> child =
                    schemaNode.getChild(toId(childNodeQName));
            checkArgument(child.isPresent(), "Child nodes %s not present", childNodeQName);
            checkArgument(child.get() instanceof LeafSetNode<?>, "Child nodes %s not present", childNodeQName);
            for (final LeafSetEntryNode<?> childNode : ((LeafSetNode<?>) child.get()).getValue()) {
                extractedValues.add(getValueOfSimpleNode(childNode).get());
            }
            return extractedValues;
        }

        private static Optional<String> getSingleChildNodeValue(final DataContainerNode<?> schemaNode,
                                                                final QName childNode) {
            final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> node =
                    schemaNode.getChild(toId(childNode));
            if (node.isPresent()) {
                return getValueOfSimpleNode(node.get());
            }
            LOG.debug("Child node {} not present", childNode);
            return Optional.empty();
        }

        private static Optional<String> getValueOfSimpleNode(
                final NormalizedNode<? extends YangInstanceIdentifier.PathArgument, ?> node) {
            final String valueStr = node.getValue().toString();
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

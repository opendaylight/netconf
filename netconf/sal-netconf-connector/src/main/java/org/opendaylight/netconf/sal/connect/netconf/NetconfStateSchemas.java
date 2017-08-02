/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DATA_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds QNames for all yang modules reported by ietf-netconf-monitoring/state/schemas.
 */
public final class NetconfStateSchemas implements NetconfDeviceSchemas {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfStateSchemas.class);

    public static final NetconfStateSchemas EMPTY = new NetconfStateSchemas(Collections.emptySet());

    private static final YangInstanceIdentifier STATE_SCHEMAS_IDENTIFIER =
            YangInstanceIdentifier.builder().node(NetconfState.QNAME).node(Schemas.QNAME).build();

    private static final ContainerNode GET_SCHEMAS_RPC;

    static {
        final DataContainerChild<?, ?> filter = NetconfMessageTransformUtil.toFilterStructure(STATE_SCHEMAS_IDENTIFIER,
                BaseSchema.BASE_NETCONF_CTX_WITH_NOTIFICATIONS.getSchemaContext());
        GET_SCHEMAS_RPC
                = Builders.containerBuilder().withNodeIdentifier(toId(NETCONF_GET_QNAME)).withChild(filter).build();
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
        return Sets.newHashSet(Collections2.transform(getAvailableYangSchemas(), RemoteYangSchema::getQName));
    }

    /**
     * Issue get request to remote device and parse response to find all schemas under netconf-state/schemas.
     */
    static NetconfStateSchemas create(final DOMRpcService deviceRpc,
                                  final NetconfSessionPreferences remoteSessionCapabilities, final RemoteDeviceId id) {
        if (remoteSessionCapabilities.isMonitoringSupported() == false) {
            // TODO - need to search for get-schema support, not just ietf-netconf-monitoring support
            // issue might be a deviation to ietf-netconf-monitoring where get-schema is unsupported...
            LOG.warn("{}: Netconf monitoring not supported on device, cannot detect provided schemas", id);
            return EMPTY;
        }

        final DOMRpcResult schemasNodeResult;
        try {
            schemasNodeResult = deviceRpc.invokeRpc(toPath(NETCONF_GET_QNAME), GET_SCHEMAS_RPC).get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(id
                    + ": Interrupted while waiting for response to " + STATE_SCHEMAS_IDENTIFIER, e);
        } catch (final ExecutionException e) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed", id, STATE_SCHEMAS_IDENTIFIER, e);
            return EMPTY;
        }

        if (schemasNodeResult.getErrors().isEmpty() == false) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed, {}",
                    id, STATE_SCHEMAS_IDENTIFIER, schemasNodeResult.getErrors());
            return EMPTY;
        }

        final Optional<? extends NormalizedNode<?, ?>> schemasNode = findSchemasNode(schemasNodeResult.getResult());

        if (schemasNode.isPresent()) {
            Preconditions.checkState(schemasNode.get() instanceof ContainerNode,
                    "Expecting container containing schemas, but was %s", schemasNode.get());
            return create(id, (ContainerNode) schemasNode.get());
        } else {
            LOG.warn("{}: Unable to detect available schemas, get to {} was empty", id, STATE_SCHEMAS_IDENTIFIER);
            return EMPTY;
        }
    }

    /**
     * Parse response of get(netconf-state/schemas) to find all schemas under netconf-state/schemas.
     */
    @VisibleForTesting
    protected static NetconfStateSchemas create(final RemoteDeviceId id, final ContainerNode schemasNode) {
        final Set<RemoteYangSchema> availableYangSchemas = Sets.newHashSet();

        final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> child =
                schemasNode.getChild(toId(Schema.QNAME));
        Preconditions.checkState(child.isPresent(),
                "Unable to find list: %s in response: %s", Schema.QNAME.withoutRevision(), schemasNode);
        Preconditions.checkState(child.get() instanceof MapNode,
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

    private static Optional<? extends NormalizedNode<?, ?>> findSchemasNode(final NormalizedNode<?, ?> result) {
        if (result == null) {
            return Optional.absent();
        }
        final Optional<DataContainerChild<?, ?>> dataNode =
                ((DataContainerNode<?>) result).getChild(toId(NETCONF_DATA_QNAME));
        if (dataNode.isPresent() == false) {
            return Optional.absent();
        }

        final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> nStateNode =
                ((DataContainerNode<?>) dataNode.get()).getChild(toId(NetconfState.QNAME));
        if (nStateNode.isPresent() == false) {
            return Optional.absent();
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
            Preconditions.checkArgument(
                    schemaNode.getNodeType().equals(Schema.QNAME), "Wrong QName %s", schemaNode.getNodeType());

            QName childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_FORMAT;

            final String formatAsString = getSingleChildNodeValue(schemaNode, childNode).get();

            if (formatAsString.equals(Yang.QNAME.toString()) == false) {
                LOG.debug("{}: Ignoring schema due to unsupported format: {}", id, formatAsString);
                return Optional.absent();
            }

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_LOCATION;
            final Set<String> locationsAsString = getAllChildNodeValues(schemaNode, childNode);
            if (locationsAsString.contains(Schema.Location.Enumeration.NETCONF.toString()) == false) {
                LOG.debug("{}: Ignoring schema due to unsupported location: {}", id, locationsAsString);
                return Optional.absent();
            }

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_NAMESPACE;
            final Optional<String> namespaceValue = getSingleChildNodeValue(schemaNode, childNode);
            if (!namespaceValue.isPresent()) {
                LOG.warn("{}: Ignoring schema due to missing namespace", id);
                return Optional.absent();
            }
            final String namespaceAsString = namespaceValue.get();

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_VERSION;
            // Revision does not have to be filled
            final Optional<String> revisionAsString = getSingleChildNodeValue(schemaNode, childNode);

            childNode = NetconfMessageTransformUtil.IETF_NETCONF_MONITORING_SCHEMA_IDENTIFIER;
            final String moduleNameAsString = getSingleChildNodeValue(schemaNode, childNode).get();

            final QName moduleQName = revisionAsString.isPresent()
                    ? QName.create(namespaceAsString, revisionAsString.get(), moduleNameAsString)
                    : QName.create(URI.create(namespaceAsString), null, moduleNameAsString);

            return Optional.of(new RemoteYangSchema(moduleQName));
        }

        /**
         * Extracts all values of a leaf-list node as a set of strings.
         */
        private static Set<String> getAllChildNodeValues(final DataContainerNode<?> schemaNode,
                                                         final QName childNodeQName) {
            final Set<String> extractedValues = Sets.newHashSet();
            final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> child =
                    schemaNode.getChild(toId(childNodeQName));
            Preconditions.checkArgument(child.isPresent(), "Child nodes %s not present", childNodeQName);
            Preconditions.checkArgument(child.get() instanceof LeafSetNode<?>,
                    "Child nodes %s not present", childNodeQName);
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
            } else {
                LOG.debug("Child node {} not present", childNode);
                return Optional.absent();
            }
        }

        private static Optional<String> getValueOfSimpleNode(
                final NormalizedNode<? extends YangInstanceIdentifier.PathArgument, ?> node) {
            final Object value = node.getValue();
            return value == null || Strings.isNullOrEmpty(value.toString())
                    ? Optional.absent() : Optional.of(value.toString().trim());
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

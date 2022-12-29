/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.GET_SCHEMA_QNAME;
import static org.opendaylight.netconf.util.NetconfUtil.NETCONF_DATA_QNAME;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public final class NetconfRemoteSchemaYangSourceProvider implements SchemaSourceProvider<YangTextSchemaSource> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfRemoteSchemaYangSourceProvider.class);
    private static final NodeIdentifier FORMAT_PATHARG =
            NodeIdentifier.create(QName.create(NetconfMessageTransformUtil.GET_SCHEMA_QNAME, "format").intern());
    private static final NodeIdentifier GET_SCHEMA_PATHARG =
            NodeIdentifier.create(NetconfMessageTransformUtil.GET_SCHEMA_QNAME);
    private static final NodeIdentifier IDENTIFIER_PATHARG =
            NodeIdentifier.create(QName.create(NetconfMessageTransformUtil.GET_SCHEMA_QNAME, "identifier").intern());
    private static final NodeIdentifier VERSION_PATHARG =
            NodeIdentifier.create(QName.create(NetconfMessageTransformUtil.GET_SCHEMA_QNAME, "version").intern());
    private static final LeafNode<?> FORMAT_LEAF =
            Builders.leafBuilder().withNodeIdentifier(FORMAT_PATHARG).withValue(Yang.QNAME).build();
    private static final QName NETCONF_DATA =
            QName.create(GET_SCHEMA_QNAME, NETCONF_DATA_QNAME.getLocalName()).intern();
    private static final NodeIdentifier NETCONF_DATA_PATHARG = NodeIdentifier.create(NETCONF_DATA);

    private final DOMRpcService rpc;
    private final RemoteDeviceId id;

    public NetconfRemoteSchemaYangSourceProvider(final RemoteDeviceId id, final DOMRpcService rpc) {
        this.id = id;
        this.rpc = requireNonNull(rpc);
    }

    public static @NonNull ContainerNode createGetSchemaRequest(final String moduleName,
            final Optional<String> revision) {
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder = Builders.containerBuilder()
                .withNodeIdentifier(GET_SCHEMA_PATHARG)
                .withChild(ImmutableNodes.leafNode(IDENTIFIER_PATHARG, moduleName))
                .withChild(FORMAT_LEAF);
        if (revision.isPresent()) {
            builder.withChild(ImmutableNodes.leafNode(VERSION_PATHARG, revision.get()));
        }
        return builder.build();
    }

    private static Optional<String> getSchemaFromRpc(final RemoteDeviceId id, final NormalizedNode result) {
        if (result == null) {
            return Optional.empty();
        }

        final Optional<DataContainerChild> child = ((ContainerNode) result).findChildByArg(NETCONF_DATA_PATHARG);
        checkState(child.isPresent() && child.get() instanceof DOMSourceAnyxmlNode,
                "%s Unexpected response to get-schema, expected response with one child %s, but was %s", id,
                NETCONF_DATA, result);

        final DOMSource wrappedNode = ((DOMSourceAnyxmlNode) child.get()).body();
        final Element dataNode = (Element) requireNonNull(wrappedNode.getNode());

        return Optional.of(dataNode.getTextContent().trim());
    }

    @Override
    public ListenableFuture<YangTextSchemaSource> getSource(final SourceIdentifier sourceIdentifier) {
        final String moduleName = sourceIdentifier.name().getLocalName();

        final Optional<String> revision = Optional.ofNullable(sourceIdentifier.revision()).map(Revision::toString);
        final ContainerNode getSchemaRequest = createGetSchemaRequest(moduleName, revision);
        LOG.trace("{}: Loading YANG schema source for {}:{}", id, moduleName, revision);
        return Futures.transform(
            rpc.invokeRpc(NetconfMessageTransformUtil.GET_SCHEMA_QNAME, getSchemaRequest), input -> {
                // Transform composite node to string schema representation and then to ASTSchemaSource.
                if (input.errors().isEmpty()) {
                    final Optional<String> schemaString = getSchemaFromRpc(id, input.value());
                    checkState(schemaString.isPresent(),
                        "%s: Unexpected response to get-schema, schema not present in message for: %s", id,
                        sourceIdentifier);
                    LOG.debug("{}: YANG Schema successfully retrieved for {}:{}", id, moduleName, revision);
                    return new NetconfYangTextSchemaSource(id, sourceIdentifier, moduleName,
                        schemaString.get().getBytes(StandardCharsets.UTF_8));
                }

                LOG.warn("{}: YANG schema was not successfully retrieved for {}. Errors: {}", id, sourceIdentifier,
                    input.errors());
                throw new IllegalStateException(String.format(
                    "%s: YANG schema was not successfully retrieved for %s. Errors: %s", id, sourceIdentifier,
                    input.errors()));
            }, MoreExecutors.directExecutor());
    }

    static class NetconfYangTextSchemaSource extends YangTextSchemaSource {
        private final RemoteDeviceId id;
        private final byte[] schemaBytes;
        private final String symbolicName;

        NetconfYangTextSchemaSource(final RemoteDeviceId id, final SourceIdentifier sourceIdentifier,
                final String symbolicName, final byte[] schemaBytes) {
            super(sourceIdentifier);
            this.symbolicName = requireNonNull(symbolicName);
            this.id = id;
            this.schemaBytes = schemaBytes.clone();
        }

        @Override
        protected MoreObjects.ToStringHelper addToStringAttributes(final MoreObjects.ToStringHelper toStringHelper) {
            return toStringHelper.add("device", id);
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(schemaBytes);
        }

        @Override
        public Optional<String> getSymbolicName() {
            return Optional.of(symbolicName);
        }
    }
}

/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema;

import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.GET_SCHEMA_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DATA_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public final class NetconfRemoteSchemaYangSourceProvider implements SchemaSourceProvider<YangTextSchemaSource> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfRemoteSchemaYangSourceProvider.class);

    private final DOMRpcService rpc;
    private final RemoteDeviceId id;

    public NetconfRemoteSchemaYangSourceProvider(final RemoteDeviceId id, final DOMRpcService rpc) {
        this.id = id;
        this.rpc = Preconditions.checkNotNull(rpc);
    }

    private static final NodeIdentifier FORMAT_PATHARG =
            new NodeIdentifier(QName.create(NetconfMessageTransformUtil.GET_SCHEMA_QNAME, "format").intern());
    private static final NodeIdentifier GET_SCHEMA_PATHARG =
            new NodeIdentifier(NetconfMessageTransformUtil.GET_SCHEMA_QNAME);
    private static final NodeIdentifier IDENTIFIER_PATHARG =
            new NodeIdentifier(QName.create(NetconfMessageTransformUtil.GET_SCHEMA_QNAME, "identifier").intern());
    private static final NodeIdentifier VERSION_PATHARG =
            new NodeIdentifier(QName.create(NetconfMessageTransformUtil.GET_SCHEMA_QNAME, "version").intern());

    private static final LeafNode<?> FORMAT_LEAF =
            Builders.leafBuilder().withNodeIdentifier(FORMAT_PATHARG).withValue(Yang.QNAME).build();

    private static final QName NETCONF_DATA =
            QName.create(GET_SCHEMA_QNAME, NETCONF_DATA_QNAME.getLocalName()).intern();
    private static final NodeIdentifier NETCONF_DATA_PATHARG = toId(NETCONF_DATA);

    public static ContainerNode createGetSchemaRequest(final String moduleName, final Optional<String> revision) {
        final LeafNode<?> identifier =
                Builders.leafBuilder().withNodeIdentifier(IDENTIFIER_PATHARG).withValue(moduleName).build();
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> builder = Builders.containerBuilder()
                .withNodeIdentifier(GET_SCHEMA_PATHARG).withChild(identifier).withChild(FORMAT_LEAF);

        if (revision.isPresent()) {
            builder.withChild(Builders.leafBuilder()
                    .withNodeIdentifier(VERSION_PATHARG).withValue(revision.get()).build());
        }

        return builder.build();
    }

    private static Optional<String> getSchemaFromRpc(final RemoteDeviceId id, final NormalizedNode<?, ?> result) {
        if (result == null) {
            return Optional.absent();
        }

        final java.util.Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> child =
                ((ContainerNode) result).getChild(NETCONF_DATA_PATHARG);

        Preconditions.checkState(child.isPresent() && child.get() instanceof AnyXmlNode,
                "%s Unexpected response to get-schema, expected response with one child %s, but was %s", id,
                NETCONF_DATA, result);

        final DOMSource wrappedNode = ((AnyXmlNode) child.get()).getValue();
        Preconditions.checkNotNull(wrappedNode.getNode());
        final Element dataNode = (Element) wrappedNode.getNode();

        return Optional.of(dataNode.getTextContent().trim());
    }

    @Override
    public ListenableFuture<YangTextSchemaSource> getSource(final SourceIdentifier sourceIdentifier) {
        final String moduleName = sourceIdentifier.getName();

        final Optional<String> revision = Optional.fromJavaUtil(sourceIdentifier.getRevision().map(Revision::toString));
        final NormalizedNode<?, ?> getSchemaRequest = createGetSchemaRequest(moduleName, revision);

        LOG.trace("{}: Loading YANG schema source for {}:{}", id, moduleName,
                revision);

        return Futures.transform(
            rpc.invokeRpc(SchemaPath.create(true, NetconfMessageTransformUtil.GET_SCHEMA_QNAME), getSchemaRequest),
                new ResultToYangSourceTransformer(id, sourceIdentifier, moduleName, revision),
                MoreExecutors.directExecutor());
    }

    /**
     * Transform composite node to string schema representation and then to ASTSchemaSource.
     */
    private static final class ResultToYangSourceTransformer implements
            Function<DOMRpcResult, YangTextSchemaSource> {

        private final RemoteDeviceId id;
        private final SourceIdentifier sourceIdentifier;
        private final String moduleName;
        private final Optional<String> revision;

        ResultToYangSourceTransformer(final RemoteDeviceId id, final SourceIdentifier sourceIdentifier,
                final String moduleName, final Optional<String> revision) {
            this.id = id;
            this.sourceIdentifier = sourceIdentifier;
            this.moduleName = moduleName;
            this.revision = revision;
        }

        @Override
        public YangTextSchemaSource apply(final DOMRpcResult input) {

            if (input.getErrors().isEmpty()) {

                final Optional<String> schemaString = getSchemaFromRpc(id, input.getResult());

                Preconditions.checkState(schemaString.isPresent(),
                        "%s: Unexpected response to get-schema, schema not present in message for: %s",
                        id, sourceIdentifier);

                LOG.debug("{}: YANG Schema successfully retrieved for {}:{}",
                        id, moduleName, revision);
                return new NetconfYangTextSchemaSource(id, sourceIdentifier, schemaString);
            }

            LOG.warn(
                    "{}: YANG schema was not successfully retrieved for {}. Errors: {}",
                    id, sourceIdentifier, input.getErrors());

            throw new IllegalStateException(String.format(
                    "%s: YANG schema was not successfully retrieved for %s. Errors: %s", id, sourceIdentifier,
                    input.getErrors()));
        }

    }

    static class NetconfYangTextSchemaSource extends YangTextSchemaSource {
        private final RemoteDeviceId id;
        private final Optional<String> schemaString;

        NetconfYangTextSchemaSource(final RemoteDeviceId id, final SourceIdentifier sourceIdentifier,
                                           final Optional<String> schemaString) {
            super(sourceIdentifier);
            this.id = id;
            this.schemaString = schemaString;
        }

        @Override
        protected MoreObjects.ToStringHelper addToStringAttributes(final MoreObjects.ToStringHelper toStringHelper) {
            return toStringHelper.add("device", id);
        }

        @Override
        public InputStream openStream() throws IOException {
            return new ByteArrayInputStream(schemaString.get().getBytes(StandardCharsets.UTF_8));
        }
    }
}

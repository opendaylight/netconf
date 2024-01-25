/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.GetSchema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.get.schema.output.Data;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * A {@link SchemaSourceProvider} producing {@link YangTextSchemaSource}s based on a device's
 * {@code ietf-netconf-monitoring} interface. The set of available sources is not pre-determined and each request is
 * dispatched to the device, i.e. this provider reflects real-time updates to available schemas.
 */
public final class MonitoringSchemaSourceProvider implements SchemaSourceProvider<YangTextSchemaSource> {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringSchemaSourceProvider.class);
    private static final NodeIdentifier FORMAT_PATHARG =
            NodeIdentifier.create(QName.create(GetSchema.QNAME, "format").intern());
    private static final NodeIdentifier GET_SCHEMA_PATHARG = NodeIdentifier.create(GetSchema.QNAME);
    private static final NodeIdentifier IDENTIFIER_PATHARG =
            NodeIdentifier.create(QName.create(GetSchema.QNAME, "identifier").intern());
    private static final NodeIdentifier VERSION_PATHARG =
            NodeIdentifier.create(QName.create(GetSchema.QNAME, "version").intern());
    private static final LeafNode<?> FORMAT_LEAF =
            Builders.leafBuilder().withNodeIdentifier(FORMAT_PATHARG).withValue(Yang.QNAME).build();
    private static final NodeIdentifier NETCONF_DATA_PATHARG = NodeIdentifier.create(Data.QNAME);

    private final DOMRpcService rpc;
    private final RemoteDeviceId id;

    public MonitoringSchemaSourceProvider(final RemoteDeviceId id, final DOMRpcService rpc) {
        this.id = requireNonNull(id);
        this.rpc = requireNonNull(rpc);
    }

    public static @NonNull ContainerNode createGetSchemaRequest(final String moduleName,
            final Optional<String> revision) {
        final var builder = Builders.containerBuilder()
            .withNodeIdentifier(GET_SCHEMA_PATHARG)
            .withChild(ImmutableNodes.leafNode(IDENTIFIER_PATHARG, moduleName))
            .withChild(FORMAT_LEAF);
        revision.ifPresent(rev -> builder.withChild(ImmutableNodes.leafNode(VERSION_PATHARG, rev)));
        return builder.build();
    }

    private static Optional<String> getSchemaFromRpc(final RemoteDeviceId id, final ContainerNode result) {
        if (result == null) {
            return Optional.empty();
        }

        final DataContainerChild child = result.childByArg(NETCONF_DATA_PATHARG);
        checkState(child instanceof DOMSourceAnyxmlNode,
                "%s Unexpected response to get-schema, expected response with one child %s, but was %s", id,
                Data.QNAME, result);

        final DOMSource wrappedNode = ((DOMSourceAnyxmlNode) child).body();
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
            rpc.invokeRpc(GetSchema.QNAME, getSchemaRequest), result -> {
                // Transform composite node to string schema representation and then to ASTSchemaSource.
                if (result.errors().isEmpty()) {
                    final String schemaString = getSchemaFromRpc(id, result.value())
                        .orElseThrow(() -> new IllegalStateException(
                            id + ": Unexpected response to get-schema, schema not present in message for: "
                                + sourceIdentifier));
                    LOG.debug("{}: YANG Schema successfully retrieved for {}:{}", id, moduleName, revision);
                    return new CachedYangTextSchemaSource(id, sourceIdentifier, moduleName, schemaString);
                }

                LOG.warn("{}: YANG schema was not successfully retrieved for {}. Errors: {}", id, sourceIdentifier,
                    result.errors());
                throw new IllegalStateException(String.format(
                    "%s: YANG schema was not successfully retrieved for %s. Errors: %s", id, sourceIdentifier,
                    result.errors()));
            }, MoreExecutors.directExecutor());
    }
}

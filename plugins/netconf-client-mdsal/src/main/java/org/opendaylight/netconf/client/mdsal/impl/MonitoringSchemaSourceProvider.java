/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.GetSchema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.get.schema.output.Data;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.model.spi.source.StringYangTextSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * A {@link SchemaSourceProvider} producing {@link YangTextSource}s based on a device's
 * {@code ietf-netconf-monitoring} interface. The set of available sources is not pre-determined and each request is
 * dispatched to the device, i.e. this provider reflects real-time updates to available schemas.
 */
final class MonitoringSchemaSourceProvider implements SchemaSourceProvider<YangTextSource> {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringSchemaSourceProvider.class);
    private static final NodeIdentifier FORMAT_PATHARG =
            NodeIdentifier.create(QName.create(GetSchema.QNAME, "format").intern());
    private static final NodeIdentifier GET_SCHEMA_PATHARG = NodeIdentifier.create(GetSchema.QNAME);
    private static final NodeIdentifier IDENTIFIER_PATHARG =
            NodeIdentifier.create(QName.create(GetSchema.QNAME, "identifier").intern());
    private static final NodeIdentifier VERSION_PATHARG =
            NodeIdentifier.create(QName.create(GetSchema.QNAME, "version").intern());
    private static final LeafNode<?> FORMAT_LEAF = ImmutableNodes.leafNode(FORMAT_PATHARG, Yang.QNAME);
    private static final NodeIdentifier NETCONF_DATA_PATHARG = NodeIdentifier.create(Data.QNAME);

    private final NetconfRpcService rpc;
    private final RemoteDeviceId id;

    MonitoringSchemaSourceProvider(final RemoteDeviceId id, final NetconfRpcService rpc) {
        this.id = requireNonNull(id);
        this.rpc = requireNonNull(rpc);
    }

    @Override
    public ListenableFuture<YangTextSource> getSource(final SourceIdentifier sourceIdentifier) {
        final var moduleName = sourceIdentifier.name().getLocalName();
        final var revision = sourceIdentifier.revision();
        final var getSchemaRequest = createGetSchemaRequest(moduleName, revision);

        LOG.trace("{}: Loading YANG schema source for {}:{}", id, moduleName, revision);
        return Futures.transform(rpc.invokeNetconf(GetSchema.QNAME, getSchemaRequest),
            result -> {
                // Transform composite node to string schema representation and then to ASTSchemaSource.
                if (result.errors().isEmpty()) {
                    final String schemaString = getSchemaFromRpc(id, result.value())
                        .orElseThrow(() -> new IllegalStateException(
                            id + ": Unexpected response to get-schema, schema not present in message for: "
                                + sourceIdentifier));
                    LOG.debug("{}: YANG Schema successfully retrieved for {}:{}", id, moduleName, revision);
                    return new StringYangTextSource(sourceIdentifier, schemaString);
                }

                LOG.warn("{}: YANG schema was not successfully retrieved for {}. Errors: {}", id, sourceIdentifier,
                    result.errors());
                final var errorMessage = String.format("%s: YANG schema was not successfully retrieved for %s."
                        + " Errors: %s", id, sourceIdentifier, result.errors());
                if (isDisconnected(result)) {
                    throw new DisconnectedException(errorMessage);
                }

                throw new IllegalStateException(errorMessage);
            }, MoreExecutors.directExecutor());
    }

    private static boolean isDisconnected(DOMRpcResult result) {
        return result.errors().stream()
            .filter(e -> ErrorType.TRANSPORT.equals(e.getErrorType()))
            .anyMatch(e -> e.getMessage() != null && (e.getMessage().contains("is disconnected")
                || e.getMessage().contains("Session closed")));
    }

    static @NonNull ContainerNode createGetSchemaRequest(final String moduleName, final @Nullable Revision revision) {
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(GET_SCHEMA_PATHARG)
            .withChild(ImmutableNodes.leafNode(IDENTIFIER_PATHARG, moduleName))
            .withChild(ImmutableNodes.leafNode(VERSION_PATHARG, revision == null ? "" : revision.toString()))
            .withChild(FORMAT_LEAF)
            .build();
    }

    private static Optional<String> getSchemaFromRpc(final RemoteDeviceId id, final ContainerNode result) {
        if (result == null) {
            return Optional.empty();
        }

        final var child = result.childByArg(NETCONF_DATA_PATHARG);
        checkState(child instanceof DOMSourceAnyxmlNode,
                "%s Unexpected response to get-schema, expected response with one child %s, but was %s", id,
                Data.QNAME, result);

        final var wrappedNode = ((DOMSourceAnyxmlNode) child).body();
        final var dataNode = (Element) requireNonNull(wrappedNode.getNode());

        return Optional.of(dataNode.getTextContent().trim());
    }

    static class DisconnectedException extends RuntimeException {
        DisconnectedException(final String message) {
            super(message);
        }
    }
}

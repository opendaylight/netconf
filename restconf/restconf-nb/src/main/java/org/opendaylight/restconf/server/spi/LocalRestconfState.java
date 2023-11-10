/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.AbstractReplayParam;
import org.opendaylight.restconf.api.query.ChangedLeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.ChildNodesOnlyParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.FilterParam;
import org.opendaylight.restconf.api.query.LeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.SkipNotificationDataParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Capabilities;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

/**
 * A {@link RestconfStreamRegistry} which exposes a navigable {@link ContainerNode}-equivalent view of
 * {@link RestconfState}.
 */
@NonNullByDefault
public class LocalRestconfState extends AbstractRestconfStreamRegistry {
    public static final NodeIdentifier RESTCONF_STATE_NODEID = NodeIdentifier.create(RestconfState.QNAME);
    public static final NodeIdentifier CAPABILITIES_NODEID = NodeIdentifier.create(Capabilities.QNAME);
    public static final NodeIdentifier CAPABILITY_NODEID =
        NodeIdentifier.create(QName.create(RestconfState.QNAME, "capability").intern());
    public static final SystemLeafSetNode<String> CAPABILITY = Builders.<String>leafSetBuilder()
        .withNodeIdentifier(CAPABILITY_NODEID)
        .withChildValue(DepthParam.capabilityUri().toString())
        .withChildValue(FieldsParam.capabilityUri().toString())
        .withChildValue(FilterParam.capabilityUri().toString())
        .withChildValue(AbstractReplayParam.capabilityUri().toString())
        .withChildValue(WithDefaultsParam.capabilityUri().toString())
        .withChildValue(PrettyPrintParam.capabilityUri().toString())
        .withChildValue(LeafNodesOnlyParam.capabilityUri().toString())
        .withChildValue(ChangedLeafNodesOnlyParam.capabilityUri().toString())
        .withChildValue(SkipNotificationDataParam.capabilityUri().toString())
        .withChildValue(ChildNodesOnlyParam.capabilityUri().toString())
        .build();
    private static final ContainerNode CAPABILITIES = Builders.containerBuilder()
        .withNodeIdentifier(CAPABILITIES_NODEID)
        .withChild(CAPABILITY)
        .build();

    private final ConcurrentHashMap<NodeIdentifierWithPredicates, MapEntryNode> streams = new ConcurrentHashMap<>();

    public LocalRestconfState(final boolean useWebsockets) {
        super(useWebsockets);
    }

    public @Nullable NormalizedNode lookupPath(final YangInstanceIdentifier path) {
        final var it = path.getPathArguments().iterator();
        if (!it.hasNext()) {
            throw new IllegalArgumentException("Path must not be empty");
        }
        if (!RESTCONF_STATE_NODEID.equals(it.next())) {
            return null;
        }
        if (!it.hasNext()) {
            return buildRestconfState();
        }
        final var next = it.next();
        if (CAPABILITIES_NODEID.equals(next)) {
            return lookupPath(CAPABILITIES, it);
        } if (!STREAMS_NODEID.equals(next)) {
            return null;
        }

        if (!it.hasNext()) {
            return buildStreams();
        }
        if (!STREAM_NODEID.equals(it.next())) {
            return null;
        }
        if (!it.hasNext()) {
            return buildStream();
        }
        if (it.next() instanceof NodeIdentifierWithPredicates streamName) {
            final var stream = lookupStream(streamName);
            return stream == null ? null : lookupPath(stream, it);
        }
        return null;
    }

    private static @Nullable NormalizedNode lookupPath(final NormalizedNode parent, final Iterator<PathArgument> it) {
        var current = parent;
        while (it.hasNext()) {
            final var optChild = NormalizedNodes.getDirectChild(current, it.next());
            if (optChild.isEmpty()) {
                return null;
            }
            current = optChild.orElseThrow();
        }
        return current;
    }

    private ContainerNode buildRestconfState() {
        return Builders.containerBuilder()
            .withNodeIdentifier(RESTCONF_STATE_NODEID)
            .withChild(CAPABILITIES)
            .withChild(buildStreams())
            .build();
    }

    private ContainerNode buildStreams() {
        return Builders.containerBuilder(1).withNodeIdentifier(STREAMS_NODEID).withChild(buildStream()).build();
    }

    private SystemMapNode buildStream() {
        return Builders.mapBuilder(streams.size())
            .withNodeIdentifier(STREAM_NODEID)
            .withValue(streams.values())
            .build();
    }

    public final @Nullable MapEntryNode lookupStream(final NodeIdentifierWithPredicates streamName) {
        return streams.get(streamName);
    }

    @Override
    protected final ListenableFuture<?> putStream(final MapEntryNode stream) {
        streams.put(stream.name(), stream);
        streamAdded(stream);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected final ListenableFuture<?> deleteStream(final NodeIdentifierWithPredicates streamName) {
        streams.remove(streamName);
        streamRemoved(streamName);
        return Futures.immediateVoidFuture();
    }

    protected void streamAdded(final MapEntryNode stream) {
        // No-op
    }

    protected void streamRemoved(final NodeIdentifierWithPredicates streamName) {
        // No-op
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("streams", streams.size());
    }
}

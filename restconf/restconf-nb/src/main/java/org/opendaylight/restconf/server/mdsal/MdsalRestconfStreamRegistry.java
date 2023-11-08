/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * This singleton class is responsible for creation, removal and searching for {@link RestconfStream}s.
 */
@Singleton
@Component(factory = MdsalRestconfStreamRegistry.FACTORY_NAME, service = RestconfStream.Registry.class)
public final class MdsalRestconfStreamRegistry extends AbstractRestconfStreamRegistry {
    public static final String FACTORY_NAME = "org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker";

    private static final YangInstanceIdentifier RESTCONF_STATE_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RestconfState.QNAME),
        NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));
    private static final String USE_WEBSOCKETS_PROP = ".useWebsockets";

    private final DOMDataBroker dataBroker;

    public MdsalRestconfStreamRegistry(final DOMDataBroker dataBroker, final boolean useWebsockets) {
        super(useWebsockets);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Inject
    public MdsalRestconfStreamRegistry(final DOMDataBroker dataBroker) {
        this(dataBroker, false);
    }

    @Activate
    public MdsalRestconfStreamRegistry(@Reference final DOMDataBroker dataBroker, final Map<String, ?> props) {
        this(dataBroker, (boolean) requireNonNull(props.get(USE_WEBSOCKETS_PROP)));
    }

    public static Map<String, ?> props(final boolean useSSE) {
        return Map.of(USE_WEBSOCKETS_PROP, !useSSE);
    }

    @Override
    protected ListenableFuture<?> putStream(final MapEntryNode stream) {
        // Now issue a put operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, RESTCONF_STATE_STREAMS.node(stream.name()), stream);
        return tx.commit();
    }

    @Override
    protected ListenableFuture<?> deleteStream(final NodeIdentifierWithPredicates streamName) {
        // Now issue a delete operation while the name is still protected by being associated in the map.
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, RESTCONF_STATE_STREAMS.node(streamName));
        return tx.commit();
    }
}

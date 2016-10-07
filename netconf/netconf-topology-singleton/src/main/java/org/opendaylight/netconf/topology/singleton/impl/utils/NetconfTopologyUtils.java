/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import akka.cluster.Member;
import akka.util.Timeout;
import java.io.File;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import scala.concurrent.duration.Duration;

public class NetconfTopologyUtils {

    private static final String DEFAULT_SCHEMA_REPOSITORY_NAME = "sal-netconf-connector";

    public static final Timeout TIMEOUT = new Timeout(Duration.create(10, "seconds"));

    public static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 60000L;
    public static final int DEFAULT_KEEPALIVE_DELAY = 0;
    public static final boolean DEFAULT_RECONNECT_ON_CHANGED_SCHEMA = false;
    public static final int DEFAULT_CONCURRENT_RPC_LIMIT = 0;
    public static final int DEFAULT_MAX_CONNECTION_ATTEMPTS = 0;
    public static final int DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS = 2000;
    public static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 20000L;
    public static final BigDecimal DEFAULT_SLEEP_FACTOR = new BigDecimal(1.5);


    // The default cache directory relative to <code>CACHE_DIRECTORY</code>

    public static final String DEFAULT_CACHE_DIRECTORY = "schema";

    // Filesystem based caches are stored relative to the cache directory.
    public static final String CACHE_DIRECTORY = "cache";

    // The qualified schema cache directory <code>cache/schema</code>
    public static final String QUALIFIED_DEFAULT_CACHE_DIRECTORY =
            CACHE_DIRECTORY + File.separator + DEFAULT_CACHE_DIRECTORY;

    // The default schema repository in the case that one is not specified.
    public static final SharedSchemaRepository DEFAULT_SCHEMA_REPOSITORY =
            new SharedSchemaRepository(DEFAULT_SCHEMA_REPOSITORY_NAME);


     // The default <code>FilesystemSchemaSourceCache</code>, which stores cached files in <code>cache/schema</code>.
    public static final FilesystemSchemaSourceCache<YangTextSchemaSource> DEFAULT_CACHE =
            new FilesystemSchemaSourceCache<>(DEFAULT_SCHEMA_REPOSITORY, YangTextSchemaSource.class,
                    new File(QUALIFIED_DEFAULT_CACHE_DIRECTORY));

    // The default factory for creating <code>SchemaContext</code> instances.
    public static final SchemaContextFactory DEFAULT_SCHEMA_CONTEXT_FACTORY =
            DEFAULT_SCHEMA_REPOSITORY.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);

    public static RemoteDeviceId createRemoteDeviceId(final NodeId nodeId, final NetconfNode node) {
        IpAddress ipAddress = node.getHost().getIpAddress();
        InetSocketAddress address = new InetSocketAddress(ipAddress.getIpv4Address() != null
                ? ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue(),
                node.getPort().getValue());
        return new RemoteDeviceId(nodeId.getValue(), address);
    }

    public static String createActorPath(Member member, String name) {
        return  member.address().toString() + "/user/" + name;
    }

    public static String createMasterActorName(String name) {
        return "master_" + name;
    }

    public static NodeId getNodeId(final InstanceIdentifier.PathArgument pathArgument) {
        if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {

            final Identifier key = ((InstanceIdentifier.IdentifiableItem) pathArgument).getKey();
            if (key instanceof NodeKey) {
                return ((NodeKey) key).getNodeId();
            }
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }

    public static KeyedInstanceIdentifier<Topology, TopologyKey> createTopologyListPath(final String topologyId) {
        final InstanceIdentifier<NetworkTopology> networkTopology = InstanceIdentifier.create(NetworkTopology.class);
        return networkTopology.child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
    }

    public static InstanceIdentifier<Node> createTopologyNodeListPath(final NodeKey key, final String topologyId) {
        return createTopologyListPath(topologyId)
                .child(Node.class, new NodeKey(new NodeId(key.getNodeId().getValue())));
    }

    public static InstanceIdentifier<Node> createTopologyNodePath(final String topologyId) {
        return createTopologyListPath(topologyId).child(Node.class);
    }
}

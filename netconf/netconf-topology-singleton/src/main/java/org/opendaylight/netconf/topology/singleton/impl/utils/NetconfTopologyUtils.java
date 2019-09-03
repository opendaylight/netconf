/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.File;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.NetconfStateSchemasResolverImpl;
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
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.repo.util.InMemorySchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.ASTSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToASTTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfTopologyUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyUtils.class);

    private static final String DEFAULT_SCHEMA_REPOSITORY_NAME = "sal-netconf-connector";

    public static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 60000L;
    public static final int DEFAULT_KEEPALIVE_DELAY = 0;
    public static final boolean DEFAULT_RECONNECT_ON_CHANGED_SCHEMA = false;
    public static final boolean DEFAULT_IS_TCP_ONLY = false;
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

    public static final InMemorySchemaSourceCache<ASTSchemaSource> DEFAULT_AST_CACHE =
            InMemorySchemaSourceCache.createSoftCache(DEFAULT_SCHEMA_REPOSITORY, ASTSchemaSource.class);

    // The default factory for creating <code>SchemaContext</code> instances.
    public static final EffectiveModelContextFactory DEFAULT_SCHEMA_CONTEXT_FACTORY =
            DEFAULT_SCHEMA_REPOSITORY.createEffectiveModelContextFactory();

    /**
     * Keeps track of initialized Schema resources.  A Map is maintained in which the key represents the name
     * of the schema cache directory, and the value is a corresponding <code>SchemaResourcesDTO</code>.  The
     * <code>SchemaResourcesDTO</code> is essentially a container that allows for the extraction of the
     * <code>SchemaRegistry</code> and <code>SchemaContextFactory</code> which should be used for a particular
     * Netconf mount.  Access to <code>SCHEMA_RESOURCES_DTO_MAP</code> should be surrounded by appropriate
     * synchronization locks.
     */
    private static final Map<String, NetconfDevice.SchemaResourcesDTO> SCHEMA_RESOURCES_DTO_MAP = new HashMap<>();

    // Initializes default constant instances for the case when the default schema repository
    // directory cache/schema is used.
    static {
        SCHEMA_RESOURCES_DTO_MAP.put(DEFAULT_CACHE_DIRECTORY,
                new NetconfDevice.SchemaResourcesDTO(DEFAULT_SCHEMA_REPOSITORY, DEFAULT_SCHEMA_REPOSITORY,
                        DEFAULT_SCHEMA_CONTEXT_FACTORY, new NetconfStateSchemasResolverImpl()));
        DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(DEFAULT_AST_CACHE);
        DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(
                TextToASTTransformer.create(DEFAULT_SCHEMA_REPOSITORY, DEFAULT_SCHEMA_REPOSITORY));

        /*
         * Create the default <code>FilesystemSchemaSourceCache</code>, which stores cached files
         * in <code>cache/schema</code>. Try up to 3 times - we've seen intermittent failures on jenkins where
         * FilesystemSchemaSourceCache throws an IAE due to mkdirs failure. The theory is that there's a race
         * creating the dir and it already exists when mkdirs is called (mkdirs returns false in this case). In this
         * scenario, a retry should succeed.
         */
        int tries = 1;
        while (true) {
            try {
                FilesystemSchemaSourceCache<YangTextSchemaSource> defaultCache =
                        new FilesystemSchemaSourceCache<>(DEFAULT_SCHEMA_REPOSITORY, YangTextSchemaSource.class,
                                new File(QUALIFIED_DEFAULT_CACHE_DIRECTORY));
                DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(defaultCache);
                break;
            } catch (IllegalArgumentException e) {
                if (tries++ >= 3) {
                    LOG.error("Error creating default schema cache", e);
                    break;
                }
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }
        }
    }

    private NetconfTopologyUtils() {

    }

    public static NetconfDevice.SchemaResourcesDTO setupSchemaCacheDTO(final Node node) {
        final NetconfNode netconfNode = node.augmentation(NetconfNode.class);
        final String moduleSchemaCacheDirectory = netconfNode.getSchemaCacheDirectory();
        final RemoteDeviceId deviceId = createRemoteDeviceId(node.getNodeId(), netconfNode);

        // Setup information related to the SchemaRegistry, SchemaResourceFactory, etc.
        NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = null;
        // Only checks to ensure the String is not empty or null;  further checks related to directory accessibility
        // and file permissions are handled during the FilesystemSchemaSourceCache initialization.
        if (!Strings.isNullOrEmpty(moduleSchemaCacheDirectory)) {
            // If a custom schema cache directory is specified, create the backing DTO; otherwise, the SchemaRegistry
            // and SchemaContextFactory remain the default values.
            if (!moduleSchemaCacheDirectory.equals(DEFAULT_CACHE_DIRECTORY)) {
                // Multiple modules may be created at once;  synchronize to avoid issues with data consistency among
                // threads.
                synchronized (SCHEMA_RESOURCES_DTO_MAP) {
                    // Look for the cached DTO to reuse SchemaRegistry and SchemaContextFactory variables if
                    // they already exist
                    schemaResourcesDTO = SCHEMA_RESOURCES_DTO_MAP.get(moduleSchemaCacheDirectory);
                    if (schemaResourcesDTO == null) {
                        schemaResourcesDTO = createSchemaResourcesDTO(moduleSchemaCacheDirectory);
                        schemaResourcesDTO.getSchemaRegistry().registerSchemaSourceListener(
                                TextToASTTransformer.create((SchemaRepository) schemaResourcesDTO.getSchemaRegistry(),
                                        schemaResourcesDTO.getSchemaRegistry())
                        );
                        SCHEMA_RESOURCES_DTO_MAP.put(moduleSchemaCacheDirectory, schemaResourcesDTO);
                    }
                }
                LOG.info("{} : netconf connector will use schema cache directory {} instead of {}",
                        deviceId, moduleSchemaCacheDirectory, DEFAULT_CACHE_DIRECTORY);
            }
        }

        if (schemaResourcesDTO == null) {
            synchronized (SCHEMA_RESOURCES_DTO_MAP) {
                schemaResourcesDTO = SCHEMA_RESOURCES_DTO_MAP.get(DEFAULT_CACHE_DIRECTORY);
            }
            LOG.info("{} : using the default directory {}",
                    deviceId, QUALIFIED_DEFAULT_CACHE_DIRECTORY);
        }

        return schemaResourcesDTO;
    }

    /**
     * Creates the backing Schema classes for a particular directory.
     *
     * @param moduleSchemaCacheDirectory The string directory relative to "cache"
     * @return A DTO containing the Schema classes for the Netconf mount.
     */
    private static NetconfDevice.SchemaResourcesDTO createSchemaResourcesDTO(final String moduleSchemaCacheDirectory) {
        final SharedSchemaRepository repository = new SharedSchemaRepository(moduleSchemaCacheDirectory);
        final EffectiveModelContextFactory schemaContextFactory
                = repository.createEffectiveModelContextFactory();

        final FilesystemSchemaSourceCache<YangTextSchemaSource> deviceCache =
                createDeviceFilesystemCache(moduleSchemaCacheDirectory, repository);
        repository.registerSchemaSourceListener(deviceCache);
        repository.registerSchemaSourceListener(InMemorySchemaSourceCache.createSoftCache(repository,
                ASTSchemaSource.class));
        return new NetconfDevice.SchemaResourcesDTO(repository, repository, schemaContextFactory,
                new NetconfStateSchemasResolverImpl());
    }

    /**
     * Creates a <code>FilesystemSchemaSourceCache</code> for the custom schema cache directory.
     *
     * @param schemaCacheDirectory The custom cache directory relative to "cache"
     * @return A <code>FilesystemSchemaSourceCache</code> for the custom schema cache directory
     */
    private static FilesystemSchemaSourceCache<YangTextSchemaSource> createDeviceFilesystemCache(
            final String schemaCacheDirectory, final SchemaSourceRegistry schemaRegistry) {
        final String relativeSchemaCacheDirectory =
                NetconfTopologyUtils.CACHE_DIRECTORY + File.separator + schemaCacheDirectory;
        return new FilesystemSchemaSourceCache<>(schemaRegistry, YangTextSchemaSource.class,
                new File(relativeSchemaCacheDirectory));
    }


    public static RemoteDeviceId createRemoteDeviceId(final NodeId nodeId, final NetconfNode node) {
        final IpAddress ipAddress = node.getHost().getIpAddress();
        final InetSocketAddress address = new InetSocketAddress(ipAddress.getIpv4Address() != null
                ? ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue(),
                node.getPort().getValue().intValue());
        return new RemoteDeviceId(nodeId.getValue(), address);
    }

    public static String createActorPath(final String masterMember, final String name) {
        return  masterMember + "/user/" + name;
    }

    public static String createMasterActorName(final String name, final String masterAddress) {
        return masterAddress.replaceAll("//", "") + "_" + name;
    }

    public static NodeId getNodeId(final InstanceIdentifier.PathArgument pathArgument) {
        if (pathArgument instanceof InstanceIdentifier.IdentifiableItem) {
            final Identifier<?> key = ((InstanceIdentifier.IdentifiableItem<?, ?>) pathArgument).getKey();
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

    public static DocumentedException createMasterIsDownException(final RemoteDeviceId id, final Exception cause) {
        return new DocumentedException(id + ":Master is down. Please try again.", cause,
                DocumentedException.ErrorType.APPLICATION, DocumentedException.ErrorTag.OPERATION_FAILED,
                DocumentedException.ErrorSeverity.WARNING);
    }
}

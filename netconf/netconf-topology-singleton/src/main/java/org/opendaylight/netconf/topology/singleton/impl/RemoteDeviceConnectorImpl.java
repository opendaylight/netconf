/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.nativ.netconf.communicator.NativeNetconfDeviceCommunicator;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfDeviceCommunicatorFactory;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.RemoteDevice;
import org.opendaylight.netconf.nativ.netconf.communicator.UserPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.util.NetconfDeviceCapabilities;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.LibraryModulesSchemas;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceBuilder;
import org.opendaylight.netconf.sal.connect.netconf.SchemalessNetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfKeystoreAdapter;
import org.opendaylight.netconf.sal.connect.netconf.schema.YangLibrarySchemaYangSourceProvider;
import org.opendaylight.netconf.topology.singleton.api.RemoteDeviceConnector;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfConnectorDTO;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteDeviceConnectorImpl implements RemoteDeviceConnector {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteDeviceConnectorImpl.class);

    // Initializes default constant instances for the case when the default schema repository
    // directory cache/schema is used.

    private final NetconfTopologySetup netconfTopologyDeviceSetup;
    private final RemoteDeviceId remoteDeviceId;
    private final DeviceActionFactory deviceActionFactory;
    private final NetconfDeviceCommunicatorFactory netconfDeviceCommunicatorFactory;

    // FIXME: this seems to be a builder-like transition between {start,stop}RemoteDeviceConnection. More documentation
    //        is needed, as to what the lifecycle is here.
    private NetconfConnectorDTO deviceCommunicatorDTO;

    public RemoteDeviceConnectorImpl(final NetconfTopologySetup netconfTopologyDeviceSetup,
            final RemoteDeviceId remoteDeviceId, final DeviceActionFactory deviceActionFactory) {
        this.netconfTopologyDeviceSetup = requireNonNull(netconfTopologyDeviceSetup);
        this.remoteDeviceId = remoteDeviceId;
        this.deviceActionFactory = requireNonNull(deviceActionFactory);
        netconfDeviceCommunicatorFactory = netconfTopologyDeviceSetup.getNetconfDeviceCommunicatorFactory();
        new NetconfKeystoreAdapter(netconfTopologyDeviceSetup.getDataBroker(),
                netconfDeviceCommunicatorFactory.getKeystore());
    }

    @Override
    public void startRemoteDeviceConnection(final RemoteDeviceHandler<NetconfSessionPreferences> deviceHandler) {

        final NetconfNode netconfNode = netconfTopologyDeviceSetup.getNode().augmentation(NetconfNode.class);
        final NodeId nodeId = netconfTopologyDeviceSetup.getNode().getNodeId();
        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getPort());

        this.deviceCommunicatorDTO = createDeviceCommunicator(nodeId, netconfNode, deviceHandler);
        final NativeNetconfDeviceCommunicator deviceCommunicator = deviceCommunicatorDTO.getCommunicator();
        final ListenableFuture<NetconfDeviceCapabilities> future = deviceCommunicator.initializeRemoteConnection();

        Futures.addCallback(future, new FutureCallback<NetconfDeviceCapabilities>() {
            @Override
            public void onSuccess(final NetconfDeviceCapabilities result) {
                LOG.debug("{}: Connector started successfully", remoteDeviceId);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("{}: Connector failed", remoteDeviceId, throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void stopRemoteDeviceConnection() {
        if (deviceCommunicatorDTO != null) {
            try {
                deviceCommunicatorDTO.close();
            } catch (final Exception e) {
                LOG.error("{}: Error at closing device communicator.", remoteDeviceId, e);
            }
        }
    }

    @VisibleForTesting
    NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId, final NetconfNode node,
                                                 final RemoteDeviceHandler<NetconfSessionPreferences> deviceHandler) {
        //setup default values since default value is not supported in mdsal
        final long defaultRequestTimeoutMillis = node.getDefaultRequestTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_REQUEST_TIMEOUT_MILLIS : node.getDefaultRequestTimeoutMillis().toJava();
        final long keepaliveDelay = node.getKeepaliveDelay() == null
                ? NetconfTopologyUtils.DEFAULT_KEEPALIVE_DELAY : node.getKeepaliveDelay().toJava();
        final boolean reconnectOnChangedSchema = node.isReconnectOnChangedSchema() == null
                ? NetconfTopologyUtils.DEFAULT_RECONNECT_ON_CHANGED_SCHEMA : node.isReconnectOnChangedSchema();

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade = requireNonNull(deviceHandler);
        if (keepaliveDelay > 0) {
            LOG.info("{}: Adding keepalive facade.", remoteDeviceId);
            salFacade = new KeepaliveSalFacade(remoteDeviceId, salFacade,
                    netconfTopologyDeviceSetup.getKeepaliveExecutor(), keepaliveDelay,
                    defaultRequestTimeoutMillis);
        }

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = netconfTopologyDeviceSetup.getSchemaResourcesDTO();

        // pre register yang library sources as fallback schemas to schema registry
        // FIXME: this list not used anywhere. Should it be retained or discarded? (why?)
        //        it would seem those registrations should be bound to NetconfConnectorDTO
        final List<SchemaSourceRegistration<YangTextSchemaSource>> registeredYangLibSources = Lists.newArrayList();
        if (node.getYangLibrary() != null) {
            final String yangLibURL = node.getYangLibrary().getYangLibraryUrl().getValue();
            final String yangLibUsername = node.getYangLibrary().getUsername();
            final String yangLigPassword = node.getYangLibrary().getPassword();

            final LibraryModulesSchemas libraryModulesSchemas;
            if (yangLibURL != null) {
                if (yangLibUsername != null && yangLigPassword != null) {
                    libraryModulesSchemas = LibraryModulesSchemas.create(yangLibURL, yangLibUsername, yangLigPassword);
                } else {
                    libraryModulesSchemas = LibraryModulesSchemas.create(yangLibURL);
                }

                for (final Map.Entry<SourceIdentifier, URL> sourceIdentifierURLEntry :
                        libraryModulesSchemas.getAvailableModels().entrySet()) {
                    registeredYangLibSources
                            .add(schemaResourcesDTO.getSchemaRegistry().registerSchemaSource(
                                    new YangLibrarySchemaYangSourceProvider(remoteDeviceId,
                                            libraryModulesSchemas.getAvailableModels()),
                                    PotentialSchemaSource
                                            .create(sourceIdentifierURLEntry.getKey(), YangTextSchemaSource.class,
                                                    PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
                }
            }
        }

        final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> device;
        if (node.isSchemaless()) {
            device = new SchemalessNetconfDevice(netconfTopologyDeviceSetup.getBaseSchemas(), remoteDeviceId,
                salFacade);
        } else {
            device = new NetconfDeviceBuilder()
                    .setReconnectOnSchemasChange(reconnectOnChangedSchema)
                    .setSchemaResourcesDTO(schemaResourcesDTO)
                    .setGlobalProcessingExecutor(netconfTopologyDeviceSetup.getProcessingExecutor())
                    .setBaseSchemas(netconfTopologyDeviceSetup.getBaseSchemas())
                    .setId(remoteDeviceId)
                    .setDeviceActionFactory(deviceActionFactory)
                    .setSalFacade(salFacade)
                    .build();
        }

        final Optional<NetconfSessionPreferences> userCapabilities = getUserCapabilities(node);
        final int rpcMessageLimit =
                node.getConcurrentRpcLimit() == null
                        ? NetconfTopologyUtils.DEFAULT_CONCURRENT_RPC_LIMIT : node.getConcurrentRpcLimit().toJava();

        if (rpcMessageLimit < 1) {
            LOG.info("{}: Concurrent rpc limit is smaller than 1, no limit will be enforced.", remoteDeviceId);
        }

        final NativeNetconfDeviceCommunicator netconfDeviceCommunicator = userCapabilities
                .isPresent()
                        ? netconfDeviceCommunicatorFactory.create(remoteDeviceId, device,
                                new UserPreferences(userCapabilities.get(),
                                        node.getYangModuleCapabilities() == null ? false
                                                : node.getYangModuleCapabilities().isOverride(),
                                        node.getNonModuleCapabilities() == null ? false
                                                : node.getNonModuleCapabilities().isOverride()),
                                node)
                        : netconfDeviceCommunicatorFactory.create(remoteDeviceId, device, node);

        if (salFacade instanceof KeepaliveSalFacade) {
            ((KeepaliveSalFacade)salFacade).setListener(netconfDeviceCommunicator);
        }
        return new NetconfConnectorDTO(netconfDeviceCommunicator, salFacade);
    }

    private static Optional<NetconfSessionPreferences> getUserCapabilities(final NetconfNode node) {
        if (node.getYangModuleCapabilities() == null && node.getNonModuleCapabilities() == null) {
            return Optional.empty();
        }
        final List<String> capabilities = new ArrayList<>();

        if (node.getYangModuleCapabilities() != null) {
            capabilities.addAll(node.getYangModuleCapabilities().getCapability());
        }

        //non-module capabilities should not exist in yang module capabilities
        final NetconfSessionPreferences netconfSessionPreferences = NetconfSessionPreferences.fromStrings(capabilities);
        checkState(netconfSessionPreferences.getNonModuleCaps().isEmpty(),
                "List yang-module-capabilities/capability should contain only module based capabilities. "
                        + "Non-module capabilities used: " + netconfSessionPreferences.getNonModuleCaps());

        if (node.getNonModuleCapabilities() != null) {
            capabilities.addAll(node.getNonModuleCapabilities().getCapability());
        }

        return Optional.of(NetconfSessionPreferences.fromStrings(capabilities, CapabilityOrigin.UserDefined));
    }
}

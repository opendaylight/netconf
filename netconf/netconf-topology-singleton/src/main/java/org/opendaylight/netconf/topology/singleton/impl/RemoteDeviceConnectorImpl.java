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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.ReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.TimedReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.LibraryModulesSchemas;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceBuilder;
import org.opendaylight.netconf.sal.connect.netconf.SchemalessNetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.auth.DatastoreBackedPublicKeyAuth;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.listener.UserPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfKeystoreAdapter;
import org.opendaylight.netconf.sal.connect.netconf.schema.YangLibrarySchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.util.SslHandlerFactoryImpl;
import org.opendaylight.netconf.topology.singleton.api.RemoteDeviceConnector;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.spi.NetconfConnectorDTO;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.OdlHelloMessageCapabilities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.key.auth.KeyBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencrypted;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Decimal64;
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
    private final String privateKeyPath;
    private final String privateKeyPassphrase;
    private final AAAEncryptionService encryptionService;
    private final NetconfKeystoreAdapter keystoreAdapter;
    private final DeviceActionFactory deviceActionFactory;

    // FIXME: this seems to be a builder-like transition between {start,stop}RemoteDeviceConnection. More documentation
    //        is needed, as to what the lifecycle is here.
    private NetconfConnectorDTO deviceCommunicatorDTO;

    public RemoteDeviceConnectorImpl(final NetconfTopologySetup netconfTopologyDeviceSetup,
            final RemoteDeviceId remoteDeviceId, final DeviceActionFactory deviceActionFactory) {
        this.netconfTopologyDeviceSetup = requireNonNull(netconfTopologyDeviceSetup);
        this.remoteDeviceId = remoteDeviceId;
        this.deviceActionFactory = requireNonNull(deviceActionFactory);
        privateKeyPath = netconfTopologyDeviceSetup.getPrivateKeyPath();
        privateKeyPassphrase = netconfTopologyDeviceSetup.getPrivateKeyPassphrase();
        encryptionService = netconfTopologyDeviceSetup.getEncryptionService();
        keystoreAdapter = new NetconfKeystoreAdapter(netconfTopologyDeviceSetup.getDataBroker());
    }

    @Override
    public void startRemoteDeviceConnection(final RemoteDeviceHandler<NetconfSessionPreferences> deviceHandler) {

        final NetconfNode netconfNode = netconfTopologyDeviceSetup.getNode().augmentation(NetconfNode.class);
        final NodeId nodeId = netconfTopologyDeviceSetup.getNode().getNodeId();
        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getPort());

        deviceCommunicatorDTO = createDeviceCommunicator(nodeId, netconfNode, deviceHandler);
        final NetconfDeviceCommunicator deviceCommunicator = deviceCommunicatorDTO.getCommunicator();
        final NetconfClientSessionListener netconfClientSessionListener = deviceCommunicatorDTO.getSessionListener();
        final NetconfReconnectingClientConfiguration clientConfig =
                getClientConfig(netconfClientSessionListener, netconfNode, nodeId);
        final ListenableFuture<NetconfDeviceCapabilities> future = deviceCommunicator
                .initializeRemoteConnection(netconfTopologyDeviceSetup.getNetconfClientDispatcher(), clientConfig);

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
        final boolean reconnectOnChangedSchema = node.getReconnectOnChangedSchema() == null
                ? NetconfTopologyUtils.DEFAULT_RECONNECT_ON_CHANGED_SCHEMA : node.getReconnectOnChangedSchema();

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade = requireNonNull(deviceHandler);
        if (keepaliveDelay > 0) {
            LOG.info("{}: Adding keepalive facade.", remoteDeviceId);
            salFacade = new KeepaliveSalFacade(remoteDeviceId, salFacade,
                    netconfTopologyDeviceSetup.getKeepaliveExecutor(), keepaliveDelay,
                    defaultRequestTimeoutMillis);
        }

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = netconfTopologyDeviceSetup.getSchemaResourcesDTO();

        // pre register yang library sources as fallback schemas to schema registry
        final List<SchemaSourceRegistration<?>> registeredYangLibSources = new ArrayList<>();
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

        final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> device;
        if (node.getSchemaless()) {
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

        NetconfDeviceCommunicator netconfDeviceCommunicator = userCapabilities.isPresent()
            ? new NetconfDeviceCommunicator(remoteDeviceId, device, new UserPreferences(userCapabilities.get(),
                node.getYangModuleCapabilities() == null ? false : node.getYangModuleCapabilities().getOverride(),
                    node.getNonModuleCapabilities() == null ? false : node.getNonModuleCapabilities().getOverride()),
                rpcMessageLimit)
            : new NetconfDeviceCommunicator(remoteDeviceId, device, rpcMessageLimit);

        if (salFacade instanceof KeepaliveSalFacade) {
            ((KeepaliveSalFacade)salFacade).setListener(netconfDeviceCommunicator);
        }
        return new NetconfConnectorDTO(netconfDeviceCommunicator, salFacade, registeredYangLibSources);
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

    @VisibleForTesting
    NetconfReconnectingClientConfiguration getClientConfig(final NetconfClientSessionListener listener,
                                                           final NetconfNode node, final NodeId nodeId) {

        //setup default values since default value is not supported in mdsal
        final long clientConnectionTimeoutMillis = node.getConnectionTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_CONNECTION_TIMEOUT_MILLIS : node.getConnectionTimeoutMillis().toJava();
        final long maxConnectionAttempts = node.getMaxConnectionAttempts() == null
                ? NetconfTopologyUtils.DEFAULT_MAX_CONNECTION_ATTEMPTS : node.getMaxConnectionAttempts().toJava();
        final int betweenAttemptsTimeoutMillis = node.getBetweenAttemptsTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS
                : node.getBetweenAttemptsTimeoutMillis().toJava();
        final boolean isTcpOnly = node.getTcpOnly() == null
                ? NetconfTopologyUtils.DEFAULT_IS_TCP_ONLY : node.getTcpOnly();
        final Decimal64 sleepFactor = node.getSleepFactor() == null
                ? NetconfTopologyUtils.DEFAULT_SLEEP_FACTOR : node.getSleepFactor();

        final InetSocketAddress socketAddress = NetconfNodeUtils.toInetSocketAddress(node);

        final ReconnectStrategyFactory sf =
            new TimedReconnectStrategyFactory(netconfTopologyDeviceSetup.getEventExecutor(), maxConnectionAttempts,
                betweenAttemptsTimeoutMillis, BigDecimal.valueOf(sleepFactor.unscaledValue(), sleepFactor.scale()));


        final NetconfReconnectingClientConfigurationBuilder reconnectingClientConfigurationBuilder;
        final Protocol protocol = node.getProtocol();
        if (isTcpOnly) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
                    .withAuthHandler(getHandlerFromCredentials(node.getCredentials()));
        } else if (protocol == null || protocol.getName() == Protocol.Name.SSH) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                    .withAuthHandler(getHandlerFromCredentials(node.getCredentials()));
        } else if (protocol.getName() == Protocol.Name.TLS) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                    .withSslHandlerFactory(new SslHandlerFactoryImpl(keystoreAdapter, protocol.getSpecification()))
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS);
        } else {
            throw new IllegalStateException("Unsupported protocol type: " + protocol.getName());
        }

        final List<Uri> odlHelloCapabilities = getOdlHelloCapabilities(node);
        if (odlHelloCapabilities != null) {
            reconnectingClientConfigurationBuilder.withOdlHelloCapabilities(odlHelloCapabilities);
        }

        return reconnectingClientConfigurationBuilder
                .withNodeId(nodeId.getValue())
                .withAddress(socketAddress)
                .withConnectionTimeoutMillis(clientConnectionTimeoutMillis)
                .withReconnectStrategy(sf.createReconnectStrategy())
                .withConnectStrategyFactory(sf)
                .withSessionListener(listener)
                .build();
    }

    private static List<Uri> getOdlHelloCapabilities(final NetconfNode node) {
        final OdlHelloMessageCapabilities helloCapabilities = node.getOdlHelloMessageCapabilities();
        return helloCapabilities != null ? List.copyOf(helloCapabilities.getCapability()) : null;
    }

    private AuthenticationHandler getHandlerFromCredentials(final Credentials credentials) {
        if (credentials instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology
                .rev150114.netconf.node.credentials.credentials.LoginPassword) {
            final org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology
                    .rev150114.netconf.node.credentials.credentials.LoginPassword loginPassword
                    = (org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology
                    .rev150114.netconf.node.credentials.credentials.LoginPassword) credentials;
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPwUnencrypted) {
            final LoginPasswordUnencrypted loginPassword =
                    ((LoginPwUnencrypted) credentials).getLoginPasswordUnencrypted();
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPw) {
            final LoginPassword loginPassword = ((LoginPw) credentials).getLoginPassword();
            return new LoginPasswordHandler(loginPassword.getUsername(),
                    encryptionService.decrypt(loginPassword.getPassword()));
        }
        if (credentials instanceof KeyAuth) {
            final KeyBased keyPair = ((KeyAuth) credentials).getKeyBased();
            return new DatastoreBackedPublicKeyAuth(keyPair.getUsername(), keyPair.getKeyId(),
                    keystoreAdapter, encryptionService);
        }
        throw new IllegalStateException("Unsupported credential type: " + credentials.getClass());
    }
}

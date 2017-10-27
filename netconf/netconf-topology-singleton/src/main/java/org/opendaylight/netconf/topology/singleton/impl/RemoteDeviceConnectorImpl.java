/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.util.Timeout;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
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
import org.opendaylight.netconf.topology.singleton.api.RemoteDeviceConnector;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfConnectorDTO;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.protocol.framework.ReconnectStrategyFactory;
import org.opendaylight.protocol.framework.TimedReconnectStrategy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordDeprecated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.key.auth.KeyBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencrypted;
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
    private final DOMMountPointService mountService;
    private final Timeout actorResponseWaitTime;
    private final String privateKeyPath;
    private final String privateKeyPassphrase;
    private final AAAEncryptionService encryptionService;
    private NetconfConnectorDTO deviceCommunicatorDTO;
    private final NetconfKeystoreAdapter keystoreAdapter;

    public RemoteDeviceConnectorImpl(final NetconfTopologySetup netconfTopologyDeviceSetup,
                                     final RemoteDeviceId remoteDeviceId, final Timeout actorResponseWaitTime,
                                     final DOMMountPointService mountService) {

        this.netconfTopologyDeviceSetup = Preconditions.checkNotNull(netconfTopologyDeviceSetup);
        this.remoteDeviceId = remoteDeviceId;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.mountService = mountService;
        this.privateKeyPath = netconfTopologyDeviceSetup.getPrivateKeyPath();
        this.privateKeyPassphrase = netconfTopologyDeviceSetup.getPrivateKeyPassphrase();
        this.encryptionService = netconfTopologyDeviceSetup.getEncryptionService();
        keystoreAdapter = new NetconfKeystoreAdapter(netconfTopologyDeviceSetup.getDataBroker());
    }

    @Override
    public void startRemoteDeviceConnection(final ActorRef deviceContextActorRef) {

        final NetconfNode netconfNode = netconfTopologyDeviceSetup.getNode().getAugmentation(NetconfNode.class);
        final NodeId nodeId = netconfTopologyDeviceSetup.getNode().getNodeId();
        Preconditions.checkNotNull(netconfNode.getHost());
        Preconditions.checkNotNull(netconfNode.getPort());
        Preconditions.checkNotNull(netconfNode.isTcpOnly());

        this.deviceCommunicatorDTO = createDeviceCommunicator(nodeId, netconfNode, deviceContextActorRef);
        final NetconfDeviceCommunicator deviceCommunicator = deviceCommunicatorDTO.getCommunicator();
        final NetconfClientSessionListener netconfClientSessionListener = deviceCommunicatorDTO.getSessionListener();
        final NetconfReconnectingClientConfiguration clientConfig =
                getClientConfig(netconfClientSessionListener, netconfNode);
        final ListenableFuture<NetconfDeviceCapabilities> future = deviceCommunicator
                .initializeRemoteConnection(netconfTopologyDeviceSetup.getNetconfClientDispatcher(), clientConfig);

        Futures.addCallback(future, new FutureCallback<NetconfDeviceCapabilities>() {
            @Override
            public void onSuccess(final NetconfDeviceCapabilities result) {
                LOG.debug("{}: Connector started successfully", remoteDeviceId);
            }

            @Override
            public void onFailure(@Nullable final Throwable throwable) {
                LOG.error("{}: Connector failed, {}", remoteDeviceId, throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void stopRemoteDeviceConnection() {
        Preconditions.checkNotNull(deviceCommunicatorDTO, remoteDeviceId + ": Device communicator was not created.");
        try {
            deviceCommunicatorDTO.close();
        } catch (final Exception e) {
            LOG.error("{}: Error at closing device communicator.", remoteDeviceId, e);
        }
    }

    @VisibleForTesting
    NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId, final NetconfNode node,
                                                 final ActorRef deviceContextActorRef) {
        //setup default values since default value is not supported in mdsal
        final Long defaultRequestTimeoutMillis = node.getDefaultRequestTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_REQUEST_TIMEOUT_MILLIS : node.getDefaultRequestTimeoutMillis();
        final Long keepaliveDelay = node.getKeepaliveDelay() == null
                ? NetconfTopologyUtils.DEFAULT_KEEPALIVE_DELAY : node.getKeepaliveDelay();
        final Boolean reconnectOnChangedSchema = node.isReconnectOnChangedSchema() == null
                ? NetconfTopologyUtils.DEFAULT_RECONNECT_ON_CHANGED_SCHEMA : node.isReconnectOnChangedSchema();

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade = new MasterSalFacade(remoteDeviceId,
                netconfTopologyDeviceSetup.getActorSystem(), deviceContextActorRef, actorResponseWaitTime,
                mountService, netconfTopologyDeviceSetup.getDataBroker());
        if (keepaliveDelay > 0) {
            LOG.info("{}: Adding keepalive facade.", remoteDeviceId);
            salFacade = new KeepaliveSalFacade(remoteDeviceId, salFacade,
                    netconfTopologyDeviceSetup.getKeepaliveExecutor().getExecutor(), keepaliveDelay,
                    defaultRequestTimeoutMillis);
        }

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = netconfTopologyDeviceSetup.getSchemaResourcesDTO();


        // pre register yang library sources as fallback schemas to schema registry
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

        final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> device;
        if (node.isSchemaless()) {
            device = new SchemalessNetconfDevice(remoteDeviceId, salFacade);
        } else {
            device = new NetconfDeviceBuilder()
                    .setReconnectOnSchemasChange(reconnectOnChangedSchema)
                    .setSchemaResourcesDTO(schemaResourcesDTO)
                    .setGlobalProcessingExecutor(netconfTopologyDeviceSetup.getProcessingExecutor().getExecutor())
                    .setId(remoteDeviceId)
                    .setSalFacade(salFacade)
                    .build();
        }

        final Optional<NetconfSessionPreferences> userCapabilities = getUserCapabilities(node);
        final int rpcMessageLimit =
                node.getConcurrentRpcLimit() == null
                        ? NetconfTopologyUtils.DEFAULT_CONCURRENT_RPC_LIMIT : node.getConcurrentRpcLimit();

        if (rpcMessageLimit < 1) {
            LOG.info("{}: Concurrent rpc limit is smaller than 1, no limit will be enforced.", remoteDeviceId);
        }

        return new NetconfConnectorDTO(
                userCapabilities.isPresent() ? new NetconfDeviceCommunicator(remoteDeviceId, device,
                        new UserPreferences(userCapabilities.get(),
                                Objects.isNull(node.getYangModuleCapabilities())
                                        ? false : node.getYangModuleCapabilities().isOverride(),
                                Objects.isNull(node.getNonModuleCapabilities())
                                        ? false : node.getNonModuleCapabilities().isOverride()), rpcMessageLimit)
                        : new NetconfDeviceCommunicator(remoteDeviceId, device, rpcMessageLimit), salFacade);
    }

    private Optional<NetconfSessionPreferences> getUserCapabilities(final NetconfNode node) {
        if (node.getYangModuleCapabilities() == null && node.getNonModuleCapabilities() == null) {
            return Optional.empty();
        }
        final List<String> capabilities = new ArrayList<>();

        if (node.getYangModuleCapabilities() != null) {
            capabilities.addAll(node.getYangModuleCapabilities().getCapability());
        }

        //non-module capabilities should not exist in yang module capabilities
        final NetconfSessionPreferences netconfSessionPreferences = NetconfSessionPreferences.fromStrings(capabilities);
        Preconditions.checkState(netconfSessionPreferences.getNonModuleCaps().isEmpty(),
                "List yang-module-capabilities/capability should contain only module based capabilities. "
                        + "Non-module capabilities used: " + netconfSessionPreferences.getNonModuleCaps());

        if (node.getNonModuleCapabilities() != null) {
            capabilities.addAll(node.getNonModuleCapabilities().getCapability());
        }

        return Optional.of(NetconfSessionPreferences.fromStrings(capabilities, CapabilityOrigin.UserDefined));
    }

    //TODO: duplicate code
    private InetSocketAddress getSocketAddress(final Host host, final int port) {
        if (host.getDomainName() != null) {
            return new InetSocketAddress(host.getDomainName().getValue(), port);
        } else {
            final IpAddress ipAddress = host.getIpAddress();
            final String ip = ipAddress.getIpv4Address() != null ? ipAddress.getIpv4Address().getValue() :
                    ipAddress.getIpv6Address().getValue();
            return new InetSocketAddress(ip, port);
        }
    }

    @VisibleForTesting
    NetconfReconnectingClientConfiguration getClientConfig(final NetconfClientSessionListener listener,
                                                           final NetconfNode node) {

        //setup default values since default value is not supported in mdsal
        final long clientConnectionTimeoutMillis = node.getConnectionTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_CONNECTION_TIMEOUT_MILLIS : node.getConnectionTimeoutMillis();
        final long maxConnectionAttempts = node.getMaxConnectionAttempts() == null
                ? NetconfTopologyUtils.DEFAULT_MAX_CONNECTION_ATTEMPTS : node.getMaxConnectionAttempts();
        final int betweenAttemptsTimeoutMillis = node.getBetweenAttemptsTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS : node.getBetweenAttemptsTimeoutMillis();
        final BigDecimal sleepFactor = node.getSleepFactor() == null
                ? NetconfTopologyUtils.DEFAULT_SLEEP_FACTOR : node.getSleepFactor();

        final InetSocketAddress socketAddress = getSocketAddress(node.getHost(), node.getPort().getValue());

        final ReconnectStrategyFactory sf =
                new TimedReconnectStrategyFactory(netconfTopologyDeviceSetup.getEventExecutor(), maxConnectionAttempts,
                        betweenAttemptsTimeoutMillis, sleepFactor);
        final ReconnectStrategy strategy = sf.createReconnectStrategy();

        final AuthenticationHandler authHandler = getHandlerFromCredentials(node.getCredentials());

        return NetconfReconnectingClientConfigurationBuilder.create()
                .withAddress(socketAddress)
                .withConnectionTimeoutMillis(clientConnectionTimeoutMillis)
                .withReconnectStrategy(strategy)
                .withAuthHandler(authHandler)
                .withProtocol(node.isTcpOnly()
                        ? NetconfClientConfiguration.NetconfClientProtocol.TCP
                        : NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withConnectStrategyFactory(sf)
                .withSessionListener(listener)
                .build();
    }

    private AuthenticationHandler getHandlerFromCredentials(final Credentials credentials) {
        if (credentials instanceof LoginPasswordDeprecated) {
            final LoginPasswordDeprecated loginPassword = (LoginPasswordDeprecated) credentials;
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

    private static final class TimedReconnectStrategyFactory implements ReconnectStrategyFactory {
        private final Long connectionAttempts;
        private final EventExecutor executor;
        private final double sleepFactor;
        private final int minSleep;

        TimedReconnectStrategyFactory(final EventExecutor executor, final Long maxConnectionAttempts,
                                      final int minSleep, final BigDecimal sleepFactor) {
            if (maxConnectionAttempts != null && maxConnectionAttempts > 0) {
                connectionAttempts = maxConnectionAttempts;
            } else {
                connectionAttempts = null;
            }

            this.sleepFactor = sleepFactor.doubleValue();
            this.executor = executor;
            this.minSleep = minSleep;
        }

        @Override
        public ReconnectStrategy createReconnectStrategy() {
            final Long maxSleep = null;
            final Long deadline = null;

            return new TimedReconnectStrategy(executor, minSleep,
                    minSleep, sleepFactor, maxSleep, connectionAttempts, deadline);
        }
    }
}

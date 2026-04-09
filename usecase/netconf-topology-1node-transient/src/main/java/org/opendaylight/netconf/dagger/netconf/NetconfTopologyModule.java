/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.netconf;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientFactoryImpl;
import org.opendaylight.netconf.client.mdsal.DeviceActionFactoryImpl;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultCredentialProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultSchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.impl.DefaultSslContextFactoryProvider;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.keystore.legacy.NetconfKeystoreService;
import org.opendaylight.netconf.keystore.legacy.impl.DefaultNetconfKeystoreService;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactoryImpl;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.yangtools.yang.model.spi.source.YangTextToIRSourceTransformer;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;

@Module
@DoNotMock
@NonNullByDefault
public interface NetconfTopologyModule {

    @Provides
    @Singleton
    static DeviceActionFactory deviceActionFactory() {
        return new DeviceActionFactoryImpl();
    }

    @Provides
    @Singleton
    static BaseNetconfSchemaProvider baseNetconfSchemaProvider(final YangParserFactory factory) {
        return new DefaultBaseNetconfSchemaProvider(factory);
    }

    @Provides
    @Singleton
    static NetconfKeystoreService netconfKeystoreService(final DataBroker dataBroker,
            final RpcProviderService rpcProvider, final ClusterSingletonServiceProvider cssProvider,
            final AAAEncryptionService encryptionService, final ResourceSupport resourceSupport) {
        final var defaultNetconfKeystoreService = new DefaultNetconfKeystoreService(dataBroker, rpcProvider,
            cssProvider, encryptionService);
        resourceSupport.register(defaultNetconfKeystoreService);
        return defaultNetconfKeystoreService;
    }

    @Provides
    @Singleton
    static CredentialProvider credentialProvider(final NetconfKeystoreService keystoreService,
            final ResourceSupport resourceSupport) {
        final var defaultCredentialProvider = new DefaultCredentialProvider(keystoreService);
        resourceSupport.register(defaultCredentialProvider);
        return defaultCredentialProvider;
    }

    @Provides
    @Singleton
    static SslContextFactoryProvider sslContextFactoryProvider(final NetconfKeystoreService keystoreService,
            final ResourceSupport resourceSupport) {
        final var defaultSslContextFactoryProvider = new DefaultSslContextFactoryProvider(keystoreService);
        resourceSupport.register(defaultSslContextFactoryProvider);
        return defaultSslContextFactoryProvider;
    }

    @Provides
    @Singleton
    static NetconfClientConfigurationBuilderFactory netconfClientConfigurationBuilderFactory(
            final AAAEncryptionService encryptionService, final CredentialProvider credentialProvider,
            final SslContextFactoryProvider factoryProvider) {
        return new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider, factoryProvider);
    }

    @Provides
    @Singleton
    static SchemaResourceManager schemaResourceManager(final YangParserFactory factory,
            final YangTextToIRSourceTransformer textToIR) {
        return new DefaultSchemaResourceManager(factory, textToIR);
    }

    @Provides
    @Singleton
    static NetconfTopologySchemaAssembler netconfTopologySchemaAssembler(final ResourceSupport resourceSupport) {
        final var netconfTopologySchemaAssembler = new NetconfTopologySchemaAssembler(1);
        resourceSupport.register(netconfTopologySchemaAssembler);
        return netconfTopologySchemaAssembler;
    }

    @Provides
    @Singleton
    static NetconfTimer netconfTimer(final ResourceSupport resourceSupport) {
        final var defaultNetconfTimer = new DefaultNetconfTimer();
        resourceSupport.register(defaultNetconfTimer);
        return defaultNetconfTimer;
    }

    @Provides
    @Singleton
    static NetconfClientFactory netconfClientFactory(final NetconfTimer timer, final ResourceSupport resourceSupport) {
        final var netconfClientFactory = new NetconfClientFactoryImpl(timer);
        resourceSupport.register(netconfClientFactory);
        return netconfClientFactory;
    }

    @Provides
    @Singleton
    static NetconfTopologyImpl netconfTopologyImpl(final NetconfClientFactory clientFactory,
            final NetconfTimer timer, final NetconfTopologySchemaAssembler schemaAssembler,
            final SchemaResourceManager schemaRepositoryProvider, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final AAAEncryptionService encryptionService,
            final NetconfClientConfigurationBuilderFactory builderFactory, final RpcProviderService rpcProviderService,
            final BaseNetconfSchemaProvider baseSchemaProvider, final DeviceActionFactory deviceActionFactory,
            final ResourceSupport resourceSupport) {
        final var netconfTopology = new NetconfTopologyImpl("topology-netconf", clientFactory, timer,
            schemaAssembler, schemaRepositoryProvider, dataBroker, mountPointService, encryptionService, builderFactory,
            rpcProviderService, baseSchemaProvider, deviceActionFactory);
        resourceSupport.register(netconfTopology);
        return netconfTopology;
    }
}

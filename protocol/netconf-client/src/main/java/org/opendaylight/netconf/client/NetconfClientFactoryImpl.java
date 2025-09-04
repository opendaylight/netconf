/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.shaded.sshd.server.ServerBuilder.DH2KEX;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.TransportConstants;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.kex.BuiltinDHFactories;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tls.FixedSslHandlerFactory;
import org.opendaylight.netconf.transport.tls.TLSClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Singleton
@Component(service = NetconfClientFactory.class,  configurationPid = "org.opendaylight.netconf.client")
@Designate(ocd = NetconfClientFactoryImpl.Configuration.class)
public final class NetconfClientFactoryImpl implements NetconfClientFactory {
    private static final ImmutableList<BuiltinDHFactories> FULL_DH_FACTORIES_LIST =
        //Streams.concat(DEFAULT_KEX_PREFERENCE.stream(), Stream.of(BuiltinDHFactories.dhg14))
        Stream.of(BuiltinDHFactories.dhg1)
            .collect(ImmutableList.toImmutableList());
    private static final List<KeyExchangeFactory> FULL_KEX_PREFERENCE =
        NamedFactory.setUpTransformedFactories(true, FULL_DH_FACTORIES_LIST, DH2KEX);

    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(
            name = "default pretty-print",
            description = "Control the default value of the query parameter.")
        boolean pretty$_$print() default false;
    }

    private final SSHTransportStackFactory factory;
    private final NetconfTimer timer;

    public NetconfClientFactoryImpl(final NetconfTimer timer, final SSHTransportStackFactory factory) {
        this.timer = requireNonNull(timer);
        this.factory = requireNonNull(factory);
    }

    @Inject
    @Activate
    public NetconfClientFactoryImpl(@Reference final NetconfTimer timer) {
        // FIXME: make factory component configurable for OSGi
        this(timer, new SSHTransportStackFactory("odl-netconf-client", 0));
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        factory.close();
    }

    @Override
    public ListenableFuture<NetconfClientSession> createClient(final NetconfClientConfiguration configuration)
            throws UnsupportedConfigurationException {
        final var sessionListener = configuration.getSessionListener();
        final var transportListener = new ClientTransportChannelListener(new ClientChannelInitializer(
            createNegotiatorFactory(configuration), sessionListener));

        final var stackFuture = switch (configuration.getProtocol()) {
            case SSH -> {
                final var configurator = new ClientFactoryManagerConfigurator() {
                    @Override
                    public void configureClientFactoryManager(final ClientFactoryManager factoryManager)
                            throws UnsupportedConfigurationException {
                        if (configuration.getSshConfigurator() != null) {
                            configuration.getSshConfigurator().configureClientFactoryManager(factoryManager);
                        }
                        factoryManager.setKeyExchangeFactories(FULL_KEX_PREFERENCE);
                    }
                };
                yield factory.connectClient(TransportConstants.SSH_SUBSYSTEM, transportListener,
                    configuration.getTcpParameters(), configuration.getSshParameters(), configurator);
            }
            case TCP -> TCPClient.connect(transportListener, factory.newBootstrap(), configuration.getTcpParameters());
            case TLS -> {
                var handlerFactory = configuration.getSslHandlerFactory();
                if (handlerFactory == null) {
                    handlerFactory = new FixedSslHandlerFactory(configuration.getTlsParameters());
                }
                yield TLSClient.connect(transportListener, factory.newBootstrap(), configuration.getTcpParameters(),
                    handlerFactory);
            }
        };
        Futures.addCallback(stackFuture, transportListener, MoreExecutors.directExecutor());

        return transportListener.sessionFuture();
    }

    private @NonNull NetconfClientSessionNegotiatorFactory createNegotiatorFactory(
            final NetconfClientConfiguration configuration) {
        final var capabilities = configuration.getOdlHelloCapabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            return new NetconfClientSessionNegotiatorFactory(timer, configuration.getAdditionalHeader(),
                configuration.getConnectionTimeoutMillis(), configuration.getMaximumIncomingChunkSize());
        }
        final var stringCapabilities = capabilities.stream().map(Uri::getValue)
            .collect(ImmutableSet.toImmutableSet());
        return new NetconfClientSessionNegotiatorFactory(timer, configuration.getAdditionalHeader(),
            configuration.getConnectionTimeoutMillis(), stringCapabilities);
    }
}

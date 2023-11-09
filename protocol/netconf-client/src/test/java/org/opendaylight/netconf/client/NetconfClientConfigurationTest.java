/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.SSH;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.TCP;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.TLS;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tls.SslHandlerFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.TlsClientGrouping;

@ExtendWith(MockitoExtension.class)
class NetconfClientConfigurationTest {
    private static final String NAME = "name";
    private static final long TIMEOUT_MILLIS = 123456L;
    private static final int MAX_CHUNK_SIZE = 6789;
    private static final List<Uri> CAPABILITIES = List.of(new Uri("test::uri"));
    private static final Class<? extends Throwable> NPE = NullPointerException.class;
    private static final Class<? extends Throwable> IAE = IllegalArgumentException.class;

    @Mock
    private TcpClientGrouping tcpParams;
    @Mock
    private TlsClientGrouping tlsParams;
    @Mock
    private SslHandlerFactory sslHandlerFactory;
    @Mock
    private SshClientGrouping sshParams;
    @Mock
    private ClientFactoryManagerConfigurator sshConfigurator;
    @Mock
    private NetconfHelloMessageAdditionalHeader additionalHeader;
    @Mock
    private NetconfClientSessionListener sessionListener;

    @Test
    void tcpClientConfiguration() {
        final var cfg = baseBuilder(TCP).build();
        assertBaseConf(cfg, TCP);
    }

    @Test
    void tlsClientConfig() {
        final var cfg1 = baseBuilder(TLS).withTlsParameters(tlsParams).build();
        assertBaseConf(cfg1, TLS);
        assertSame(tlsParams, cfg1.getTlsParameters());

        final var cfg2 = baseBuilder(TLS).withSslHandlerFactory(sslHandlerFactory).build();
        assertBaseConf(cfg2, TLS);
        assertSame(sslHandlerFactory, cfg2.getSslHandlerFactory());
    }

    @Test
    void sshClientConfig() {
        final var cfg = baseBuilder(SSH).withSshParameters(sshParams)
            .withSshConfigurator(sshConfigurator).build();
        assertBaseConf(cfg, SSH);
        assertSame(sshParams, cfg.getSshParameters());
        assertSame(sshConfigurator, cfg.getSshConfigurator());
    }

    @ParameterizedTest(name = "Invalid configuration: {0}")
    @MethodSource("invalidConfigArgs")
    void invalidConfig(final String testDesc, final NetconfClientProtocol protocol,
        final BuilderModification modification, final Class<? extends Throwable> expected) {
        assertThrows(expected, () -> modification.modify(baseBuilder(protocol)).build());
    }

    private static Stream<Arguments> invalidConfigArgs() {
        return Stream.of(
            Arguments.of("No Protocol", TCP, (BuilderModification) builder -> builder.withProtocol(null), NPE),
            Arguments.of("No Netconf Session Listener",
                TCP, (BuilderModification) builder -> builder.withSessionListener(null), NPE),
            Arguments.of("No TCP params", TCP, (BuilderModification) builder -> builder.withTcpParameters(null), NPE),
            Arguments.of("No TLS params", TLS, (BuilderModification) builder -> builder, IAE),
            Arguments.of("No SSH params", SSH, (BuilderModification) builder -> builder, NPE)
        );
    }

    private NetconfClientConfigurationBuilder baseBuilder(final NetconfClientProtocol protocol) {
        return NetconfClientConfigurationBuilder.create()
            .withProtocol(protocol)
            .withTcpParameters(tcpParams)
            .withName(NAME)
            .withAdditionalHeader(additionalHeader)
            .withSessionListener(sessionListener)
            .withOdlHelloCapabilities(CAPABILITIES)
            .withConnectionTimeoutMillis(TIMEOUT_MILLIS)
            .withMaximumIncomingChunkSize(MAX_CHUNK_SIZE);
    }

    private void assertBaseConf(final NetconfClientConfiguration cfg, final NetconfClientProtocol protocol) {
        assertEquals(protocol, cfg.getProtocol());
        assertSame(tcpParams, cfg.getTcpParameters());
        assertEquals(NAME, cfg.getName());
        assertSame(additionalHeader, cfg.getAdditionalHeader().orElseThrow());
        assertSame(sessionListener, cfg.getSessionListener());
        assertEquals(CAPABILITIES, cfg.getOdlHelloCapabilities());
        assertEquals(TIMEOUT_MILLIS, cfg.getConnectionTimeoutMillis());
        assertEquals(MAX_CHUNK_SIZE, cfg.getMaximumIncomingChunkSize());
    }

    @FunctionalInterface
    private interface BuilderModification {
        NetconfClientConfigurationBuilder modify(NetconfClientConfigurationBuilder builder);
    }
}

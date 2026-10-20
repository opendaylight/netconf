/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.util.List;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.dagger.yang.parser.DaggerDefaultYangParserComponent;
import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class CallHomeNodeHandlerTest {
    private static final RemoteDeviceId DEVICE_ID = new RemoteDeviceId("netconf-topology",
        new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));
    private static final NodeId NODE_ID = new NodeId("call-home-node");
    private static final NetconfNode NETCONF_NODE = new NetconfNodeBuilder()
        .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
        .setPort(new PortNumber(Uint16.valueOf(9999)))
        .setSchemaless(true)
        .setTcpOnly(false)
        .setConcurrentRpcLimit(Uint16.ZERO)
        .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
        // no keepalives, so that the handler is connected directly to the device
        .setKeepaliveDelay(Uint32.ZERO)
        .build();

    private static BaseNetconfSchemaProvider BASE_SCHEMAS;

    @Mock
    private NetconfTimer timer;
    @Mock
    private SchemaResourceManager schemaManager;
    @Mock
    private DeviceActionFactory deviceActionFactory;
    @Mock
    private RemoteDeviceHandler delegate;
    @Mock
    private CallHomeSessionInitializer sessionInitializer;
    @Mock
    private EffectiveModelContext modelContext;
    @Mock
    private Rpcs.Normalized normalizedRpcs;
    @Captor
    private ArgumentCaptor<NegotiatedSshAlg> sshAlgCaptor;

    private final SettableFuture<NetconfClientSession> sessionFuture = SettableFuture.create();
    private NetconfTopologySchemaAssembler schemaAssembler;

    @BeforeAll
    static void beforeClass() {
        BASE_SCHEMAS = new DefaultBaseNetconfSchemaProvider(DaggerDefaultYangParserComponent.create().parserFactory());
    }

    @AfterAll
    static void afterClass() {
        BASE_SCHEMAS = null;
    }

    @BeforeEach
    void before() {
        schemaAssembler = new NetconfTopologySchemaAssembler(1);
    }

    @AfterEach
    void after() {
        schemaAssembler.close();
    }

    @Test
    void connectInitializesSessionWithCommunicator() {
        final var handler = newHandler(null);
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());

        handler.connect();

        // the transport is attached to the communicator, no outbound connection is involved
        verify(sessionInitializer).initialize(handler.communicator());
        verifyNoInteractions(timer);
    }

    @Test
    void connectTwiceFails() {
        final var handler = newHandler(null);
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());

        handler.connect();
        final var ex = assertThrows(IllegalStateException.class, handler::connect);
        assertEquals("Call-home session for " + DEVICE_ID + " has already been initialized", ex.getMessage());
        verify(sessionInitializer).initialize(handler.communicator());
    }

    @Test
    void connectAfterCloseDoesNothing() {
        final var handler = newHandler(null);
        doNothing().when(delegate).close();

        handler.close();
        handler.connect();

        verifyNoInteractions(sessionInitializer);
    }

    @Test
    void sessionFailureIsReported() {
        final var handler = newHandler(null);
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());
        handler.connect();

        final var cause = new IllegalStateException("negotiation failed");
        doNothing().when(delegate).onDeviceFailed(cause);
        sessionFuture.setException(cause);

        verify(delegate).onDeviceFailed(cause);
    }

    @Test
    void sessionFailureBeforeConnectReturnsIsReported() {
        final var handler = newHandler(null);
        final var cause = new IllegalStateException("negotiation failed");
        sessionFuture.setException(cause);
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());
        doNothing().when(delegate).onDeviceFailed(cause);

        handler.connect();

        verify(delegate).onDeviceFailed(cause);
    }

    @Test
    void sessionCancellationIsNotReported() {
        final var handler = newHandler(null);
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());
        handler.connect();

        sessionFuture.cancel(false);

        verify(delegate, never()).onDeviceFailed(any());
    }

    @Test
    void closeCancelsSessionAndClosesDelegate() {
        final var handler = newHandler(null);
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());
        doNothing().when(delegate).close();
        handler.connect();

        handler.close();

        assertTrue(sessionFuture.isCancelled());
        verify(delegate).close();
        // cancellation caused by close is not a failure
        verify(delegate, never()).onDeviceFailed(any());
    }

    @Test
    void closeBeforeConnectClosesDelegate() {
        final var handler = newHandler(null);
        doNothing().when(delegate).close();

        handler.close();

        verify(delegate).close();
        assertFalse(sessionFuture.isDone());
    }

    @Test
    void negotiatedSshAlgIsReportedOnConnect() {
        final var sshAlg = new NegotiatedSshAlg(null, null, null, null);
        assertSame(sshAlg, connectAndCaptureSshAlg(newHandler(sshAlg)));
    }

    @Test
    void noSshAlgIsReportedOnConnect() {
        // TLS call-home does not negotiate any SSH algorithms
        assertNull(connectAndCaptureSshAlg(newHandler(null)));
    }

    @Test
    void disconnectIsReportedWithoutReconnect() {
        final var handler = newHandler(null);
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());
        doNothing().when(delegate).onDeviceDisconnected();
        handler.connect();

        handler.onDeviceDisconnected();

        verify(delegate).onDeviceDisconnected();
        verify(delegate, never()).onDeviceFailed(any());
        // no reconnect is scheduled and the session is not initialized again
        verifyNoInteractions(timer);
        verify(sessionInitializer).initialize(handler.communicator());
    }

    @Test
    void failureIsReportedWithoutReconnect() {
        final var handler = newHandler(null);
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());
        final var cause = new IllegalStateException("session failed");
        doNothing().when(delegate).onDeviceFailed(cause);
        handler.connect();

        handler.onDeviceFailed(cause);

        verify(delegate).onDeviceFailed(cause);
        verifyNoInteractions(timer);
        verify(sessionInitializer).initialize(handler.communicator());
    }

    @Test
    void eventsAfterCloseAreNotReported() {
        final var handler = newHandler(null);
        doNothing().when(delegate).close();
        handler.close();

        handler.onDeviceDisconnected();
        handler.onDeviceFailed(new IllegalStateException("late failure"));

        verify(delegate, never()).onDeviceDisconnected();
        verify(delegate, never()).onDeviceFailed(any());
    }

    private CallHomeNodeHandler newHandler(final @Nullable NegotiatedSshAlg sshAlg) {
        return new CallHomeNodeHandler(timer, BASE_SCHEMAS, schemaManager, schemaAssembler, deviceActionFactory,
            delegate, DEVICE_ID, NODE_ID, NETCONF_NODE, null, sessionInitializer, sshAlg);
    }

    private NegotiatedSshAlg connectAndCaptureSshAlg(final CallHomeNodeHandler handler) {
        doReturn(sessionFuture).when(sessionInitializer).initialize(handler.communicator());
        handler.connect();

        final var schema = new NetconfDeviceSchema(DatabindContext.ofModel(modelContext),
            NetconfDeviceCapabilities.empty());
        final var preferences = NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE));
        final var services = new RemoteDeviceServices(normalizedRpcs, null);
        doNothing().when(delegate).onDeviceConnected(eq(schema), eq(preferences), eq(services), sshAlgCaptor.capture());

        // the SSH algorithms supplied by the device are ignored in favor of those known from the accepted transport
        handler.onDeviceConnected(schema, preferences, services, new NegotiatedSshAlg(null, null, null, null));

        verify(delegate).onDeviceConnected(eq(schema), eq(preferences), eq(services), sshAlgCaptor.capture());
        return sshAlgCaptor.getValue();
    }
}

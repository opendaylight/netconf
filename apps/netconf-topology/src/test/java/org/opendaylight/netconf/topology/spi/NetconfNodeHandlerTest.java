/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240110.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240110.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfNodeHandlerTest {
    private static final RemoteDeviceId DEVICE_ID = new RemoteDeviceId("netconf-topology",
        new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));
    private static final NodeId NODE_ID = new NodeId("testing-node");

    private static BaseNetconfSchemas BASE_SCHEMAS;

    // Core setup
    @Mock
    private Timer timer;
    @Mock
    private SchemaResourceManager schemaManager;
    @Mock
    private Executor processingExecutor;
    @Mock
    private DeviceActionFactory deviceActionFactory;
    @Mock
    private RemoteDeviceHandler delegate;

    // DefaultNetconfClientConfigurationBuilderFactory setup
    @Mock
    private SslHandlerFactoryProvider sslHandlerFactoryProvider;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private CredentialProvider credentialProvider;

    // Mock client dispatcher-related things
    @Mock
    private NetconfClientFactory clientFactory;
    @Mock
    private NetconfClientSession clientSession;
    @Captor
    private ArgumentCaptor<NetconfDeviceSchema> schemaCaptor;
    @Captor
    private ArgumentCaptor<NetconfSessionPreferences> prefsCaptor;
    @Captor
    private ArgumentCaptor<RemoteDeviceServices> servicesCaptor;

    // Mock Timer-related things
    @Mock
    private Timeout timeout;
    @Captor
    private ArgumentCaptor<TimerTask> timerCaptor;
    @Mock
    private EffectiveModelContext schemaContext;

    private NetconfNodeHandler handler;

    @BeforeClass
    public static void beforeClass() throws Exception {
        BASE_SCHEMAS = new DefaultBaseNetconfSchemas(new DefaultYangParserFactory());
    }

    @BeforeClass
    public static void afterClass() throws Exception {
        BASE_SCHEMAS = null;
    }

    @Before
    public void before() {
        // Instantiate the handler
        handler = new NetconfNodeHandler(clientFactory, timer, BASE_SCHEMAS, schemaManager, processingExecutor,
            new NetconfClientConfigurationBuilderFactoryImpl(encryptionService, credentialProvider,
                sslHandlerFactoryProvider),
            deviceActionFactory, delegate, DEVICE_ID, NODE_ID, new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                .setPort(new PortNumber(Uint16.valueOf(9999)))
                .setReconnectOnChangedSchema(true)
                .setSchemaless(true)
                .setTcpOnly(true)
                .setBackoffMultiplier(Decimal64.valueOf("1.5"))
                .setConcurrentRpcLimit(Uint16.ONE)
                // One reconnection attempt
                .setMaxConnectionAttempts(Uint32.TWO)
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(1000))
                .setMinBackoffMillis(Uint16.valueOf(100))
                .setKeepaliveDelay(Uint32.valueOf(1000))
                .setConnectionTimeoutMillis(Uint32.valueOf(1000))
                .setMaxBackoffMillis(Uint32.valueOf(1000))
                .setBackoffJitter(Decimal64.valueOf("0.0"))
                .setCredentials(new LoginPwUnencryptedBuilder()
                    .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                        .setUsername("testuser")
                        .setPassword("testpassword")
                        .build())
                    .build())
                .build(), null);
    }

    @Test
    public void successfulOnDeviceConnectedPropagates() throws Exception {
        assertSuccessfulConnect();
        assertEquals(1, handler.attempts());

        final var schema = new NetconfDeviceSchema(NetconfDeviceCapabilities.empty(),
            MountPointContext.of(schemaContext));
        final var netconfSessionPreferences = NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE));
        final var deviceServices = new RemoteDeviceServices(mock(Rpcs.Normalized.class), null);

        // when the device is connected, we propagate the information
        doNothing().when(delegate).onDeviceConnected(schemaCaptor.capture(), prefsCaptor.capture(),
            servicesCaptor.capture());
        handler.onDeviceConnected(schema, netconfSessionPreferences, deviceServices);

        assertEquals(schema, schemaCaptor.getValue());
        assertEquals(netconfSessionPreferences, prefsCaptor.getValue());
        assertEquals(deviceServices, servicesCaptor.getValue());
        assertEquals(0, handler.attempts());
    }

    @Test
    public void failedSchemaCausesReconnect() throws Exception {
        assertSuccessfulConnect();
        assertEquals(1, handler.attempts());

        // Note: this will count as a second attempt
        doReturn(timeout).when(timer).newTimeout(timerCaptor.capture(), anyLong(), any());

        handler.onDeviceFailed(new AssertionError("schema failure"));

        assertEquals(2, handler.attempts());

        // and when we run the task, we get a clientDispatcher invocation, but attempts are still the same
        timerCaptor.getValue().run(timeout);
        verify(clientFactory, times(2)).createClient(any());
        assertEquals(2, handler.attempts());
    }

    @Test
    public void downAfterUpCausesReconnect() throws Exception {
        // Let's borrow common bits
        successfulOnDeviceConnectedPropagates();

        // when the device is connected, we propagate the information and initiate reconnect
        doNothing().when(delegate).onDeviceDisconnected();
        doReturn(timeout).when(timer).newTimeout(timerCaptor.capture(), eq(100L), eq(TimeUnit.MILLISECONDS));
        handler.onDeviceDisconnected();

        assertEquals(1, handler.attempts());

        // and when we run the task, we get a clientDispatcher invocation, but attempts are still the same
        timerCaptor.getValue().run(timeout);
        verify(clientFactory, times(2)).createClient(any());
        assertEquals(1, handler.attempts());
    }

    @Test
    public void socketFailuresAreRetried() throws Exception {
        final var firstFuture = SettableFuture.create();
        final var secondFuture = SettableFuture.create();
        doReturn(firstFuture, secondFuture).when(clientFactory).createClient(any());
        handler.connect();
        assertEquals(1, handler.attempts());

        doReturn(timeout).when(timer).newTimeout(timerCaptor.capture(), eq(150L), eq(TimeUnit.MILLISECONDS));
        firstFuture.setException(new AssertionError("first"));

        assertEquals(2, handler.attempts());

        // and when we run the task, we get a clientDispatcher invocation, but attempts are still the same
        timerCaptor.getValue().run(timeout);
        verify(clientFactory, times(2)).createClient(any());
        assertEquals(2, handler.attempts());

        // now report the second failure
        final var throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        doNothing().when(delegate).onDeviceFailed(throwableCaptor.capture());
        secondFuture.setException(new AssertionError("second"));
        assertThat(throwableCaptor.getValue(), instanceOf(ConnectGivenUpException.class));

        // but nothing else happens
        assertEquals(2, handler.attempts());
    }

    // Initiate connect() which results in immediate clientDispatcher report. No interactions with delegate may occur,
    // as this is just a prelude to a follow-up callback
    private void assertSuccessfulConnect() throws Exception {
        doReturn(Futures.immediateFuture(clientSession)).when(clientFactory).createClient(any());
        handler.connect();
        verify(clientFactory).createClient(any());
        verifyNoInteractions(delegate);
    }
}

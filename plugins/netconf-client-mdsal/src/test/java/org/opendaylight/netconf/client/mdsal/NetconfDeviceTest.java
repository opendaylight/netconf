/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.NetconfDevice.EmptySchemaContextException;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.impl.DefaultDeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.spi.KeepaliveSalFacade;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

@ExtendWith(MockitoExtension.class)
class NetconfDeviceTest extends AbstractTestModelTest {
    private static final String TEST_NAMESPACE = "test:namespace";
    private static final String TEST_MODULE = "test-module";
    private static final String TEST_REVISION = "2013-07-22";
    private static final SourceIdentifier TEST_SID = new SourceIdentifier(TEST_MODULE, TEST_REVISION);
    private static final String TEST_CAPABILITY =
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION;

    private static NetconfMessage NOTIFICATION;

    @Mock
    private SchemaSourceRegistry schemaRegistry;
    @Mock
    private DeviceNetconfSchemaProvider schemaProvider;
    @Mock
    private RemoteDeviceHandler facade;
    @Mock
    private InitialRpcTimeoutHandler initialRpcTimeoutHandler;
    @Mock
    private NetconfDeviceCommunicator listener;
    @Mock
    private EffectiveModelContextFactory schemaFactory;
    @Mock
    private DeviceNetconfSchemaProvider deviceSchemaProvider;

    @BeforeAll
    static void setupNotification() throws Exception {
        NOTIFICATION = new NetconfMessage(XmlUtil.readXmlToDocument(
            NetconfDeviceTest.class.getResourceAsStream("/notification-payload.xml")));
    }

    @BeforeEach
    public void before() {
        when(initialRpcTimeoutHandler.decorateRpcs(any(RemoteDeviceServices.Rpcs.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );
    }

    @Test
    void testNetconfDeviceFailFirstSchemaFailSecondEmpty() {
        // Make fallback attempt to fail due to empty resolved sources
        final var schemaResolutionException = new SchemaResolutionException("fail first",
            new SourceIdentifier("test-module", "2013-07-22"), new Throwable());
        doReturn(Futures.immediateFailedFuture(schemaResolutionException))
                .when(schemaFactory).createEffectiveModelContext(anyCollection());

        doReturn(facade).when(initialRpcTimeoutHandler).remoteDeviceHandler();

        final var device = new NetconfDeviceBuilder()
            .setReconnectOnSchemasChange(true)
            .setDeviceSchemaProvider(mockDeviceNetconfSchemaProvider(getSchemaRepository(), schemaFactory))
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build();

        // Monitoring not supported
        device.onRemoteSessionUp(getSessionCaps(false, TEST_CAPABILITY), listener);

        final var captor = ArgumentCaptor.forClass(Throwable.class);
        verify(facade, timeout(5000)).onDeviceFailed(captor.capture());
        assertInstanceOf(EmptySchemaContextException.class, captor.getValue());

        verify(listener, timeout(5000)).close();
        verify(schemaFactory, times(1)).createEffectiveModelContext(anyCollection());
    }

    private static SchemaRepository getSchemaRepository() {
        final var mock = mock(SchemaRepository.class);
        final var mockRep = mock(YangTextSource.class);
        doReturn(Futures.immediateFuture(mockRep))
                .when(mock).getSchemaSource(any(SourceIdentifier.class), eq(YangTextSource.class));
        return mock;
    }

    @Test
    void testNotificationBeforeSchema() {
        final var remoteDeviceHandler = mockRemoteDeviceHandler();
        doNothing().when(remoteDeviceHandler).onNotification(any(DOMNotification.class));
        final var schemaFuture = SettableFuture.<DeviceNetconfSchema>create();
        doReturn(schemaFuture).when(deviceSchemaProvider).deviceNetconfSchemaFor(any(), any(), any(), any(), any());

        doReturn(remoteDeviceHandler).when(initialRpcTimeoutHandler).remoteDeviceHandler();

        final var device = new NetconfDeviceBuilder()
            .setReconnectOnSchemasChange(true)
            .setDeviceSchemaProvider(deviceSchemaProvider)
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build();

        final var sessionCaps = getSessionCaps(true, TEST_CAPABILITY);
        device.onRemoteSessionUp(sessionCaps, listener);

        device.onNotification(NOTIFICATION);
        device.onNotification(NOTIFICATION);
        verify(remoteDeviceHandler, times(0)).onNotification(any(DOMNotification.class));

        // Now enable schema
        schemaFuture.set(new DeviceNetconfSchema(NetconfDeviceCapabilities.empty(),
            NetconfToNotificationTest.getNotificationSchemaContext(NetconfDeviceTest.class, false)));

        verify(remoteDeviceHandler, timeout(10000).times(2))
            .onNotification(any(DOMNotification.class));

        device.onNotification(NOTIFICATION);
        verify(remoteDeviceHandler, times(3)).onNotification(any(DOMNotification.class));
    }

    private RemoteDeviceHandler mockRemoteDeviceHandler() {
        doNothing().when(facade).onDeviceConnected(
            any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
        return facade;
    }

    @Test
    void testNetconfDeviceReconnect() {
        doReturn(RpcResultBuilder.failed().buildFuture()).when(listener).sendRequest(any());
        doReturn(facade).when(initialRpcTimeoutHandler).remoteDeviceHandler();

        final var device = new NetconfDeviceBuilder()
            .setReconnectOnSchemasChange(true)
            .setDeviceSchemaProvider(mockDeviceNetconfSchemaProvider())
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build();
        final var sessionCaps = getSessionCaps(true,
                TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION);
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(facade, timeout(5000)).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));

        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected();

        device.onRemoteSessionUp(sessionCaps, listener);

        verify(facade, timeout(5000).times(2)).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
    }

    @Test
    void testNetconfDeviceDisconnectListenerCallCancellation() {
        doNothing().when(facade).onDeviceDisconnected();
        final var schemaFuture = SettableFuture.<DeviceNetconfSchema>create();
        doReturn(schemaFuture).when(schemaProvider).deviceNetconfSchemaFor(any(), any(), any(), any(), any());
        doReturn(facade).when(initialRpcTimeoutHandler).remoteDeviceHandler();

        final var device = new NetconfDeviceBuilder()
            .setReconnectOnSchemasChange(true)
            .setDeviceSchemaProvider(schemaProvider)
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build();
        //session up, start schema resolution
        device.onRemoteSessionUp(getSessionCaps(true,
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION),
            listener);
        //session closed
        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected();
        //complete schema setup
        schemaFuture.set(new DeviceNetconfSchema(NetconfDeviceCapabilities.empty(), TEST_MODEL));
        //facade.onDeviceDisconnected() was called, so facade.onDeviceConnected() shouldn't be called anymore
        verify(facade, after(1000).never()).onDeviceConnected(any(), any(), any(RemoteDeviceServices.class));
    }

    @Test
    void testNetconfDeviceReconnectBeforeSchemaSetup() {
        final var schemaFuture = SettableFuture.<EffectiveModelContext>create();
        doReturn(schemaFuture).when(schemaFactory).createEffectiveModelContext(anyCollection());
        doReturn(facade).when(initialRpcTimeoutHandler).remoteDeviceHandler();

        doReturn(RpcResultBuilder.failed().buildFuture()).when(listener).sendRequest(any());

        final var device = new NetconfDeviceBuilder()
            .setReconnectOnSchemasChange(true)
            .setDeviceSchemaProvider(mockDeviceNetconfSchemaProvider(getSchemaRepository(),
                schemaFactory))
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build();
        final var sessionCaps = getSessionCaps(true,
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION);

        // session up, start schema resolution
        device.onRemoteSessionUp(sessionCaps, listener);
        // session down
        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected();
        // session back up, start another schema resolution
        device.onRemoteSessionUp(sessionCaps, listener);
        // complete schema setup
        schemaFuture.set(TEST_MODEL);
        // schema setup performed twice
        verify(schemaFactory, timeout(5000).times(2)).createEffectiveModelContext(anyCollection());
        // onDeviceConnected called once
        verify(facade, timeout(5000)).onDeviceConnected(
            any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
    }

    @Test
    void testNetconfDeviceAvailableCapabilitiesBuilding() {
        doReturn(RpcResultBuilder.failed().buildFuture()).when(listener).sendRequest(any());
        doReturn(facade).when(initialRpcTimeoutHandler).remoteDeviceHandler();

        final var netconfSpy = spy(new NetconfDeviceBuilder()
            .setReconnectOnSchemasChange(true)
            .setDeviceSchemaProvider(mockDeviceNetconfSchemaProvider())
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build());

        final var sessionCaps = getSessionCaps(true,
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION);
        final var moduleBasedCaps = new HashMap<QName, CapabilityOrigin>();
        moduleBasedCaps.putAll(sessionCaps.moduleBasedCaps());
        moduleBasedCaps.put(QName.create("(test:qname:side:loading)test"), CapabilityOrigin.UserDefined);

        netconfSpy.onRemoteSessionUp(sessionCaps.replaceModuleCaps(moduleBasedCaps), listener);

        final var argument = ArgumentCaptor.forClass(NetconfDeviceSchema.class);
        verify(facade, timeout(5000)).onDeviceConnected(argument.capture(),
            any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));

        assertEquals(Set.of(
            new AvailableCapabilityBuilder()
                .setCapability("(test:namespace?revision=2013-07-22)test-module")
                .setCapabilityOrigin(CapabilityOrigin.DeviceAdvertised)
                .build(),
            new AvailableCapabilityBuilder()
                .setCapability("(test:qname:side:loading)test")
                .setCapabilityOrigin(CapabilityOrigin.UserDefined)
                .build()), argument.getValue().capabilities().resolvedCapabilities());
    }

    @Test
    void testNetconfDeviceNotificationsModelNotPresentWithCapability() {
        doReturn(facade).when(initialRpcTimeoutHandler).remoteDeviceHandler();
        final var netconfSpy = spy(new NetconfDeviceBuilder()
            .setDeviceSchemaProvider(mockDeviceNetconfSchemaProvider())
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build());

        netconfSpy.onRemoteSessionUp(getSessionCaps(false, CapabilityURN.NOTIFICATION),
            listener);

        final var argument = ArgumentCaptor.forClass(NetconfDeviceSchema.class);
        verify(facade, timeout(5000)).onDeviceConnected(argument.capture(), any(NetconfSessionPreferences.class),
                any(RemoteDeviceServices.class));

        assertEquals(Set.of(
            new AvailableCapabilityBuilder()
                .setCapability("(urn:ietf:params:xml:ns:yang:ietf-yang-types?revision=2013-07-15)ietf-yang-types")
                .build(),
            new AvailableCapabilityBuilder()
                .setCapability("(urn:ietf:params:xml:ns:netconf:notification:1.0?revision=2008-07-14)notifications")
                .build()), argument.getValue().capabilities().resolvedCapabilities());
    }

    @Test
    void testNetconfDeviceNotificationsModelIsPresent() {
        doReturn(facade).when(initialRpcTimeoutHandler).remoteDeviceHandler();
        final var netconfSpy = spy(new NetconfDeviceBuilder()
            .setDeviceSchemaProvider(mockDeviceNetconfSchemaProvider())
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build());

        netconfSpy.onRemoteSessionUp(getSessionCaps(false).replaceModuleCaps(Map.of(
            org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                .YangModuleInfoImpl.getInstance().getName(), CapabilityOrigin.DeviceAdvertised,
            org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                .YangModuleInfoImpl.getInstance().getName(), CapabilityOrigin.DeviceAdvertised
            )), listener);

        final var argument = ArgumentCaptor.forClass(NetconfDeviceSchema.class);
        verify(facade, timeout(5000)).onDeviceConnected(argument.capture(), any(NetconfSessionPreferences.class),
                any(RemoteDeviceServices.class));

        assertEquals(Set.of(
            new AvailableCapabilityBuilder()
                .setCapability("(urn:ietf:params:xml:ns:yang:ietf-yang-types?revision=2013-07-15)ietf-yang-types")
                .setCapabilityOrigin(CapabilityOrigin.DeviceAdvertised)
                .build(),
            new AvailableCapabilityBuilder()
                .setCapability("(urn:ietf:params:xml:ns:netconf:notification:1.0?revision=2008-07-14)notifications")
                .setCapabilityOrigin(CapabilityOrigin.DeviceAdvertised)
                .build()), argument.getValue().capabilities().resolvedCapabilities());
    }

    @Test
    void testNetconfDeviceNotificationsModelIsPresentWithKeepaliveRpcService() {
        final var spySalFacade = spy(new KeepaliveSalFacade(
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22)), mock(RemoteDeviceHandler.class),
            new DefaultNetconfTimer()));
        doReturn(spySalFacade).when(initialRpcTimeoutHandler).remoteDeviceHandler();
        final var netconfDevice = new NetconfDeviceBuilder()
            .setDeviceSchemaProvider(mockDeviceNetconfSchemaProvider())
            .setProcessingExecutor(MoreExecutors.directExecutor())
            .setId(getId())
            .setTimeoutRpc(initialRpcTimeoutHandler)
            .setBaseSchemaProvider(BASE_SCHEMAS)
            .build();

        netconfDevice.onRemoteSessionUp(getSessionCaps(false).replaceModuleCaps(Map.of(
            org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                .YangModuleInfoImpl.getInstance().getName(), CapabilityOrigin.DeviceAdvertised,
            org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                .YangModuleInfoImpl.getInstance().getName(), CapabilityOrigin.DeviceAdvertised
        )), listener);

        final var argument = ArgumentCaptor.forClass(NetconfDeviceSchema.class);
        verify(spySalFacade, timeout(5000)).onDeviceConnected(argument.capture(), any(NetconfSessionPreferences.class),
            any(RemoteDeviceServices.class));

        assertEquals(Set.of(
            new AvailableCapabilityBuilder()
                .setCapability("(urn:ietf:params:xml:ns:yang:ietf-yang-types?revision=2013-07-15)ietf-yang-types")
                .setCapabilityOrigin(CapabilityOrigin.DeviceAdvertised)
                .build(),
            new AvailableCapabilityBuilder()
                .setCapability("(urn:ietf:params:xml:ns:netconf:notification:1.0?revision=2008-07-14)notifications")
                .setCapabilityOrigin(CapabilityOrigin.DeviceAdvertised)
                .build()),
            argument.getValue().capabilities().resolvedCapabilities());
    }

    private EffectiveModelContextFactory getSchemaFactory() {
        doReturn(TEST_MODEL_FUTURE).when(schemaFactory).createEffectiveModelContext(anyCollection());
        return schemaFactory;
    }

    private DeviceNetconfSchemaProvider mockDeviceNetconfSchemaProvider() {
        return mockDeviceNetconfSchemaProvider(getSchemaRepository(), getSchemaFactory());
    }

    private DeviceNetconfSchemaProvider mockDeviceNetconfSchemaProvider(final SchemaRepository schemaRepository,
            final EffectiveModelContextFactory modelContextFactory) {
        return new DefaultDeviceNetconfSchemaProvider(schemaRegistry, schemaRepository, modelContextFactory);
    }

    private static RemoteDeviceId getId() {
        return new RemoteDeviceId("test-D", InetSocketAddress.createUnresolved("localhost", 22));
    }

    private static NetconfSessionPreferences getSessionCaps(final boolean addMonitor,
            final String... additionalCapabilities) {
        final var capabilities = new ArrayList<String>();
        capabilities.add(CapabilityURN.BASE);
        capabilities.add(CapabilityURN.BASE_1_1);
        if (addMonitor) {
            capabilities.add(NetconfState.QNAME.getNamespace().toString());
        }
        capabilities.addAll(List.of(additionalCapabilities));
        return NetconfSessionPreferences.fromStrings(capabilities);
    }
}

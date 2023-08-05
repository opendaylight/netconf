/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.NetconfDevice.EmptySchemaContextException;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceTest extends AbstractTestModelTest {
    public static final String TEST_NAMESPACE = "test:namespace";
    public static final String TEST_MODULE = "test-module";
    public static final String TEST_REVISION = "2013-07-22";
    public static final SourceIdentifier TEST_SID = new SourceIdentifier(TEST_MODULE, TEST_REVISION);
    public static final String TEST_CAPABILITY =
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION;

    public static final SourceIdentifier TEST_SID2 = new SourceIdentifier(TEST_MODULE + "2", TEST_REVISION);
    public static final String TEST_CAPABILITY2 =
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "2" + "&amp;revision=" + TEST_REVISION;

    private static final NetconfDeviceSchemasResolver STATE_SCHEMAS_RESOLVER =
        (deviceRpc, remoteSessionCapabilities, id, schemaContext) -> NetconfStateSchemas.EMPTY;

    private static NetconfMessage NOTIFICATION;

    @Mock
    private SchemaSourceRegistry schemaRegistry;

    @BeforeClass
    public static final void setupNotification() throws Exception {
        NOTIFICATION = new NetconfMessage(XmlUtil.readXmlToDocument(
            NetconfDeviceTest.class.getResourceAsStream("/notification-payload.xml")));
    }

    @Test
    public void testNetconfDeviceFlawedModelFailedResolution() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final EffectiveModelContextFactory schemaFactory = getSchemaFactory();
        final SchemaRepository schemaRepository = getSchemaRepository();

        final SchemaResolutionException schemaResolutionException =
                new SchemaResolutionException("fail first", TEST_SID, new Throwable("YangTools parser fail"));
        doAnswer(invocation -> {
            if (((Collection<?>) invocation.getArguments()[0]).size() == 2) {
                return Futures.immediateFailedFuture(schemaResolutionException);
            } else {
                return Futures.immediateFuture(SCHEMA_CONTEXT);
            }
        }).when(schemaFactory).createEffectiveModelContext(anyCollection());

        final NetconfDeviceSchemasResolver stateSchemasResolver = (deviceRpc, remoteSessionCapabilities, id,
                schemaContext) -> {
            final Module first = SCHEMA_CONTEXT.getModules().iterator().next();
            final QName qName = QName.create(first.getQNameModule(), first.getName());
            final NetconfStateSchemas.RemoteYangSchema source1 = new NetconfStateSchemas.RemoteYangSchema(qName);
            final NetconfStateSchemas.RemoteYangSchema source2 =
                    new NetconfStateSchemas.RemoteYangSchema(QName.create(first.getQNameModule(), "test-module2"));
            return new NetconfStateSchemas(Sets.newHashSet(source1, source2));
        };

        doReturn(mock(SchemaSourceRegistration.class)).when(schemaRegistry).registerSchemaSource(any(), any());
        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice
                .SchemaResourcesDTO(schemaRegistry, schemaRepository, schemaFactory, stateSchemasResolver);

        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();
        // Monitoring supported
        final NetconfSessionPreferences sessionCaps = getSessionCaps(true, List.of(TEST_CAPABILITY, TEST_CAPABILITY2));
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(facade, timeout(5000)).onDeviceConnected(any(NetconfDeviceSchema.class),
            any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
        verify(schemaFactory, times(2)).createEffectiveModelContext(anyCollection());
    }

    @Test
    public void testNetconfDeviceFailFirstSchemaFailSecondEmpty() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final EffectiveModelContextFactory schemaFactory = getSchemaFactory();
        final SchemaRepository schemaRepository = getSchemaRepository();

        // Make fallback attempt to fail due to empty resolved sources
        final SchemaResolutionException schemaResolutionException = new SchemaResolutionException("fail first",
                List.of(), ImmutableMultimap.of());
        doReturn(Futures.immediateFailedFuture(schemaResolutionException))
                .when(schemaFactory).createEffectiveModelContext(anyCollection());

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice
                .SchemaResourcesDTO(schemaRegistry, schemaRepository, schemaFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();

        // Monitoring not supported
        final NetconfSessionPreferences sessionCaps = getSessionCaps(false, List.of(TEST_CAPABILITY));
        device.onRemoteSessionUp(sessionCaps, listener);

        final var captor = ArgumentCaptor.forClass(Throwable.class);
        verify(facade, timeout(5000)).onDeviceFailed(captor.capture());
        assertThat(captor.getValue(), instanceOf(EmptySchemaContextException.class));

        verify(listener, timeout(5000)).close();
        verify(schemaFactory, times(1)).createEffectiveModelContext(anyCollection());
    }

    @Test
    public void testNetconfDeviceMissingSource() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final EffectiveModelContextFactory schemaFactory = getSchemaFactory();
        final SchemaRepository schemaRepository = getSchemaRepository();

        // Make fallback attempt to fail due to empty resolved sources
        final MissingSchemaSourceException schemaResolutionException =
                new MissingSchemaSourceException("fail first", TEST_SID);
        doReturn(Futures.immediateFailedFuture(schemaResolutionException))
                .when(schemaRepository).getSchemaSource(eq(TEST_SID), eq(YangTextSchemaSource.class));
        doAnswer(invocation -> {
            if (((Collection<?>) invocation.getArguments()[0]).size() == 2) {
                return Futures.immediateFailedFuture(schemaResolutionException);
            } else {
                return Futures.immediateFuture(SCHEMA_CONTEXT);
            }
        }).when(schemaFactory).createEffectiveModelContext(anyCollection());

        final NetconfDeviceSchemasResolver stateSchemasResolver = (deviceRpc, remoteSessionCapabilities, id,
            schemaContext) -> {
            final Module first = SCHEMA_CONTEXT.getModules().iterator().next();
            final QName qName = QName.create(first.getQNameModule(), first.getName());
            final NetconfStateSchemas.RemoteYangSchema source1 = new NetconfStateSchemas.RemoteYangSchema(qName);
            final NetconfStateSchemas.RemoteYangSchema source2 =
                    new NetconfStateSchemas.RemoteYangSchema(QName.create(first.getQNameModule(), "test-module2"));
            return new NetconfStateSchemas(Sets.newHashSet(source1, source2));
        };

        doReturn(mock(SchemaSourceRegistration.class)).when(schemaRegistry).registerSchemaSource(any(), any());
        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice
                .SchemaResourcesDTO(schemaRegistry, schemaRepository, schemaFactory, stateSchemasResolver);

        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setBaseSchemas(BASE_SCHEMAS)
                .setId(getId())
                .setSalFacade(facade)
                .build();
        // Monitoring supported
        final NetconfSessionPreferences sessionCaps =
                getSessionCaps(true, List.of(TEST_CAPABILITY, TEST_CAPABILITY2));
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(facade, timeout(5000)).onDeviceConnected(any(NetconfDeviceSchema.class),
            any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
        verify(schemaFactory, times(1)).createEffectiveModelContext(anyCollection());
    }

    private static SchemaRepository getSchemaRepository() {
        final SchemaRepository mock = mock(SchemaRepository.class);
        final YangTextSchemaSource mockRep = mock(YangTextSchemaSource.class);
        doReturn(Futures.immediateFuture(mockRep))
                .when(mock).getSchemaSource(any(SourceIdentifier.class), eq(YangTextSchemaSource.class));
        return mock;
    }

    @Test
    public void testNotificationBeforeSchema() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = mock(EffectiveModelContextFactory.class);
        final SettableFuture<SchemaContext> schemaFuture = SettableFuture.create();
        doReturn(schemaFuture).when(schemaContextProviderFactory).createEffectiveModelContext(anyCollection());
        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry,
            getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true, List.of(TEST_CAPABILITY));
        device.onRemoteSessionUp(sessionCaps, listener);

        device.onNotification(NOTIFICATION);
        device.onNotification(NOTIFICATION);
        verify(facade, times(0)).onNotification(any(DOMNotification.class));

        verify(facade, times(0)).onNotification(any(DOMNotification.class));
        schemaFuture.set(NetconfToNotificationTest.getNotificationSchemaContext(getClass(), false));
        verify(facade, timeout(10000).times(2)).onNotification(any(DOMNotification.class));

        device.onNotification(NOTIFICATION);
        verify(facade, timeout(10000).times(3)).onNotification(any(DOMNotification.class));
    }

    @Test
    public void testNetconfDeviceReconnect() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(
                schemaRegistry, getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();
        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                List.of(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(schemaContextProviderFactory, timeout(5000)).createEffectiveModelContext(anyCollection());
        verify(facade, timeout(5000)).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));

        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected();

        device.onRemoteSessionUp(sessionCaps, listener);

        verify(schemaContextProviderFactory, timeout(5000).times(2)).createEffectiveModelContext(anyCollection());
        verify(facade, timeout(5000).times(2)).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
    }

    @Test
    public void testNetconfDeviceDisconnectListenerCallCancellation() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = mock(EffectiveModelContextFactory.class);
        final SettableFuture<SchemaContext> schemaFuture = SettableFuture.create();
        doReturn(schemaFuture).when(schemaContextProviderFactory).createEffectiveModelContext(anyCollection());
        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry,
            getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();
        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                List.of(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        //session up, start schema resolution
        device.onRemoteSessionUp(sessionCaps, listener);
        //session closed
        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected();
        //complete schema setup
        schemaFuture.set(SCHEMA_CONTEXT);
        //facade.onDeviceDisconnected() was called, so facade.onDeviceConnected() shouldn't be called anymore
        verify(facade, after(1000).never()).onDeviceConnected(any(), any(), any(RemoteDeviceServices.class));
    }

    @Test
    public void testNetconfDeviceAvailableCapabilitiesBuilding() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry,
            getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();
        final NetconfDevice netconfSpy = spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                List.of(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        final Map<QName, CapabilityOrigin> moduleBasedCaps = new HashMap<>();
        moduleBasedCaps.putAll(sessionCaps.moduleBasedCaps());
        moduleBasedCaps
                .put(QName.create("(test:qname:side:loading)test"), CapabilityOrigin.UserDefined);

        netconfSpy.onRemoteSessionUp(sessionCaps.replaceModuleCaps(moduleBasedCaps), listener);

        final ArgumentCaptor<NetconfDeviceSchema> argument = ArgumentCaptor.forClass(NetconfDeviceSchema.class);
        verify(facade, timeout(5000)).onDeviceConnected(argument.capture(), any(NetconfSessionPreferences.class),
            any(RemoteDeviceServices.class));
        argument.getValue().capabilities().resolvedCapabilities()
                .forEach(entry -> assertEquals("Builded 'AvailableCapability' schemas should match input capabilities.",
                        moduleBasedCaps.get(
                                QName.create(entry.getCapability())).getName(), entry.getCapabilityOrigin().getName()));
    }

    @Test
    public void testNetconfDeviceNotificationsModelNotPresentWithCapability() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry,
            getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();
        final NetconfDevice netconfSpy = spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(false, List.of(CapabilityURN.NOTIFICATION));

        netconfSpy.onRemoteSessionUp(sessionCaps, listener);

        final ArgumentCaptor<NetconfDeviceSchema> argument = ArgumentCaptor.forClass(NetconfDeviceSchema.class);
        verify(facade, timeout(5000)).onDeviceConnected(argument.capture(), any(NetconfSessionPreferences.class),
                any(RemoteDeviceServices.class));

        List<String> notificationModulesName = List.of(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName().toString(),
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName().toString());

        final Set<AvailableCapability> resolvedCapabilities = argument.getValue().capabilities().resolvedCapabilities();

        assertEquals(2, resolvedCapabilities.size());
        assertTrue(resolvedCapabilities.stream().anyMatch(entry -> notificationModulesName
                .contains(entry.getCapability())));
    }

    @Test
    public void testNetconfDeviceNotificationsCapabilityIsNotPresent() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry,
            getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();
        final NetconfDevice netconfSpy = spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(false,
                List.of(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));

        netconfSpy.onRemoteSessionUp(sessionCaps, listener);

        final ArgumentCaptor<NetconfDeviceSchema> argument = ArgumentCaptor.forClass(NetconfDeviceSchema.class);
        verify(facade, timeout(5000)).onDeviceConnected(argument.capture(), any(NetconfSessionPreferences.class),
                any(RemoteDeviceServices.class));
        final NetconfDeviceCapabilities netconfDeviceCaps = argument.getValue().capabilities();

        List<String> notificationModulesName = List.of(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName().toString(),
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName().toString());

        assertFalse(netconfDeviceCaps.resolvedCapabilities().stream()
            .anyMatch(entry -> notificationModulesName.contains(entry.getCapability())));
    }

    @Test
    public void testNetconfDeviceNotificationsModelIsPresent() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry,
            getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(MoreExecutors.directExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .build();
        final NetconfDevice netconfSpy = spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(false, List.of());

        final Map<QName, CapabilityOrigin> moduleBasedCaps = new HashMap<>();
        moduleBasedCaps.put(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName(),
                CapabilityOrigin.DeviceAdvertised);
        moduleBasedCaps.put(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName(),
                CapabilityOrigin.DeviceAdvertised);


        netconfSpy.onRemoteSessionUp(sessionCaps.replaceModuleCaps(moduleBasedCaps), listener);

        final ArgumentCaptor<NetconfDeviceSchema> argument = ArgumentCaptor.forClass(NetconfDeviceSchema.class);
        verify(facade, timeout(5000)).onDeviceConnected(argument.capture(), any(NetconfSessionPreferences.class),
                any(RemoteDeviceServices.class));
        final Set<AvailableCapability> resolvedCapabilities = argument.getValue().capabilities().resolvedCapabilities();

        List<String> notificationModulesName = List.of(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName().toString(),
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName().toString());

        assertEquals(2, resolvedCapabilities.size());
        assertTrue(resolvedCapabilities.stream().anyMatch(entry -> notificationModulesName
                .contains(entry.getCapability())));
    }

    private static EffectiveModelContextFactory getSchemaFactory() throws Exception {
        final EffectiveModelContextFactory schemaFactory = mock(EffectiveModelContextFactory.class);
        doReturn(Futures.immediateFuture(SCHEMA_CONTEXT))
                .when(schemaFactory).createEffectiveModelContext(anyCollection());
        return schemaFactory;
    }

    private static RemoteDeviceHandler getFacade() throws Exception {
        final RemoteDeviceHandler remoteDeviceHandler = mockCloseableClass(RemoteDeviceHandler.class);
        doNothing().when(remoteDeviceHandler).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
        doNothing().when(remoteDeviceHandler).onDeviceDisconnected();
        doNothing().when(remoteDeviceHandler).onNotification(any(DOMNotification.class));
        return remoteDeviceHandler;
    }

    private static <T extends AutoCloseable> T mockCloseableClass(final Class<T> remoteDeviceHandlerClass)
            throws Exception {
        final T mock = mock(remoteDeviceHandlerClass);
        doNothing().when(mock).close();
        return mock;
    }

    public RemoteDeviceId getId() {
        return new RemoteDeviceId("test-D", InetSocketAddress.createUnresolved("localhost", 22));
    }

    public NetconfSessionPreferences getSessionCaps(final boolean addMonitor,
                                                    final Collection<String> additionalCapabilities) {
        final var capabilities = new ArrayList<String>();
        capabilities.add(CapabilityURN.BASE);
        capabilities.add(CapabilityURN.BASE_1_1);
        if (addMonitor) {
            capabilities.add(NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString());
        }
        capabilities.addAll(additionalCapabilities);
        return NetconfSessionPreferences.fromStrings(capabilities);
    }

    public NetconfDeviceCommunicator getListener() throws Exception {
        return mockCloseableClass(NetconfDeviceCommunicator.class);
    }
}

/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.MountPointManager;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.xml.sax.SAXException;

public class NetconfDeviceTest extends AbstractTestModelTest {

    private static final NetconfMessage NOTIFICATION;

    private static final ContainerNode COMPOSITE_NODE = mockClass(ContainerNode.class);
    private static final DOMNotification DOM_NOTIFICATION = mockClass(DOMNotification.class);
    private static final NetconfMessageTransformer NETCONF_MESSAGE_TRANSFORMER;
    private static final EffectiveModelContext EFFECTIVE_MODEL_CONTEXT;
    private static final MountPointContext MOUNT_POINT_CONTEXT;

    static {
        try {
            NOTIFICATION =
                    new NetconfMessage(XmlUtil.readXmlToDocument(
                            NetconfDeviceTest.class.getResourceAsStream("/notification-payload.xml")));
            EFFECTIVE_MODEL_CONTEXT =
                    YangParserTestUtils.parseYangResources(NetconfToRpcRequestTest.class,
                            "/schemas/config-test-rpc.yang", "/schemas/user-notification.yang");
            MOUNT_POINT_CONTEXT = new EmptyMountPointContext(EFFECTIVE_MODEL_CONTEXT);
            NETCONF_MESSAGE_TRANSFORMER =
                    new NetconfMessageTransformer(MOUNT_POINT_CONTEXT, true,
                            BASE_SCHEMAS.getBaseSchemaWithNotifications());
        } catch (SAXException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final DOMRpcResult RPC_RESULT = new DefaultDOMRpcResult(COMPOSITE_NODE);

    public static final String TEST_NAMESPACE = "test:namespace";
    public static final String TEST_MODULE = "test-module";
    public static final String TEST_REVISION = "2013-07-22";
    public static final SourceIdentifier TEST_SID =
            RevisionSourceIdentifier.create(TEST_MODULE, Revision.of(TEST_REVISION));
    public static final String TEST_CAPABILITY =
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION;

    public static final SourceIdentifier TEST_SID2 =
            RevisionSourceIdentifier.create(TEST_MODULE + "2", Revision.of(TEST_REVISION));
    public static final String TEST_CAPABILITY2 =
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "2" + "&amp;revision=" + TEST_REVISION;

    private static final NetconfDeviceSchemasResolver STATE_SCHEMAS_RESOLVER =
        (deviceRpc, remoteSessionCapabilities, id, schemaContext) -> NetconfStateSchemas.EMPTY;

    private MountPointManager mountPointManager = mock(MountPointManager.class);

    @Test
    public void testNetconfDeviceFlawedModelFailedResolution() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
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
            final Module first = Iterables.getFirst(SCHEMA_CONTEXT.getModules(), null);
            final QName qName = QName.create(first.getQNameModule(), first.getName());
            final NetconfStateSchemas.RemoteYangSchema source1 = new NetconfStateSchemas.RemoteYangSchema(qName);
            final NetconfStateSchemas.RemoteYangSchema source2 =
                    new NetconfStateSchemas.RemoteYangSchema(QName.create(first.getQNameModule(), "test-module2"));
            return new NetconfStateSchemas(Sets.newHashSet(source1, source2));
        };

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice
                .SchemaResourcesDTO(getSchemaRegistry(), schemaRepository, schemaFactory, stateSchemasResolver);

        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .build();
        // Monitoring supported
        final NetconfSessionPreferences sessionCaps =
                getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY, TEST_CAPABILITY2));
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(facade, timeout(5000)).onDeviceConnected(anyString(),
                any(NetconfSessionPreferences.class), any(DOMRpcService.class), isNull());
        verify(schemaFactory, times(2)).createEffectiveModelContext(anyCollection());
    }

    @Test
    public void testNetconfDeviceFailFirstSchemaFailSecondEmpty() throws Exception {
        final ArrayList<String> capList = Lists.newArrayList(TEST_CAPABILITY);

        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final EffectiveModelContextFactory schemaFactory = getSchemaFactory();
        final SchemaRepository schemaRepository = getSchemaRepository();

        // Make fallback attempt to fail due to empty resolved sources
        final SchemaResolutionException schemaResolutionException
                = new SchemaResolutionException("fail first",
                Collections.emptyList(), HashMultimap.create());
        doReturn(Futures.immediateFailedFuture(schemaResolutionException))
                .when(schemaFactory).createEffectiveModelContext(anyCollection());

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice
                .SchemaResourcesDTO(getSchemaRegistry(), schemaRepository, schemaFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .build();

        // Monitoring not supported
        final NetconfSessionPreferences sessionCaps = getSessionCaps(false, capList);
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(facade, timeout(5000)).onDeviceDisconnected(any());
        verify(listener, timeout(5000)).close();
        verify(schemaFactory, times(1)).createEffectiveModelContext(anyCollection());
    }

    @Test
    public void testNetconfDeviceMissingSource() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
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
            final Module first = Iterables.getFirst(SCHEMA_CONTEXT.getModules(), null);
            final QName qName = QName.create(first.getQNameModule(), first.getName());
            final NetconfStateSchemas.RemoteYangSchema source1 = new NetconfStateSchemas.RemoteYangSchema(qName);
            final NetconfStateSchemas.RemoteYangSchema source2 =
                    new NetconfStateSchemas.RemoteYangSchema(QName.create(first.getQNameModule(), "test-module2"));
            return new NetconfStateSchemas(Sets.newHashSet(source1, source2));
        };

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice
                .SchemaResourcesDTO(getSchemaRegistry(), schemaRepository, schemaFactory, stateSchemasResolver);

        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .setId(getId())
                .setSalFacade(facade)
                .build();
        // Monitoring supported
        final NetconfSessionPreferences sessionCaps =
                getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY, TEST_CAPABILITY2));
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(facade, timeout(5000)).onDeviceConnected(anyString(),
                any(NetconfSessionPreferences.class), any(DOMRpcService.class), isNull());
        verify(schemaFactory, times(1)).createEffectiveModelContext(anyCollection());
    }

    private static SchemaSourceRegistry getSchemaRegistry() {
        final SchemaSourceRegistry mock = mock(SchemaSourceRegistry.class);
        final SchemaSourceRegistration<?> mockReg = mock(SchemaSourceRegistration.class);
        doNothing().when(mockReg).close();
        doReturn(mockReg).when(mock).registerSchemaSource(
                any(org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider.class),
                any(PotentialSchemaSource.class));
        return mock;
    }

    private static SchemaRepository getSchemaRepository() {
        final SchemaRepository mock = mock(SchemaRepository.class);
        final SchemaSourceRepresentation mockRep = mock(SchemaSourceRepresentation.class);
        doReturn(Futures.immediateFuture(mockRep))
                .when(mock).getSchemaSource(any(SourceIdentifier.class), eq(YangTextSchemaSource.class));
        return mock;
    }

    @Test
    public void testNotificationBeforeSchema() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = mock(EffectiveModelContextFactory.class);
        final SettableFuture<SchemaContext> schemaFuture = SettableFuture.create();
        doReturn(schemaFuture).when(schemaContextProviderFactory).createEffectiveModelContext(any(Collection.class));
        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO =
                new NetconfDevice.SchemaResourcesDTO(getSchemaRegistry(), getSchemaRepository(),
                        schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .build();

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_CAPABILITY));
        device.onRemoteSessionUp(sessionCaps, listener);

        device.onNotification(NOTIFICATION);
        device.onNotification(NOTIFICATION);
        verify(facade, times(0)).onNotification(anyString(), any(DOMNotification.class));

        verify(facade, times(0)).onNotification(anyString(), any(DOMNotification.class));
        schemaFuture.set(NetconfToNotificationTest.getNotificationSchemaContext(getClass(), false));
        verify(facade, timeout(10000).times(2)).onNotification(anyString(), any(DOMNotification.class));

        device.onNotification(NOTIFICATION);
        verify(facade, timeout(10000).times(3)).onNotification(anyString(), any(DOMNotification.class));
    }

    @Test
    public void testNetconfDeviceReconnect() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO
                schemaResourcesDTO =
                new NetconfDevice.SchemaResourcesDTO(getSchemaRegistry(), getSchemaRepository(),
                        schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device =
                new NetconfDeviceBuilder().setReconnectOnSchemasChange(true)
                        .setSchemaResourcesDTO(schemaResourcesDTO)
                        .setGlobalProcessingExecutor(getExecutor())
                        .setId(getId())
                        .setSalFacade(facade)
                        .setBaseSchemas(BASE_SCHEMAS)
                        .setMountPointManager(mountPointManager)
                        .build();
        final NetconfSessionPreferences sessionCaps =
                getSessionCaps(true, Lists.newArrayList(
                        TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(schemaContextProviderFactory, timeout(5000)).createEffectiveModelContext(any(Collection.class));
        verify(facade, timeout(5000)).onDeviceConnected(anyString(), any(NetconfSessionPreferences.class),
                any(DOMRpcService.class), isNull());

        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected(anyString());

        device.onRemoteSessionUp(sessionCaps, listener);

        verify(schemaContextProviderFactory, timeout(5000).times(2)).createEffectiveModelContext(any(Collection.class));
        verify(facade, timeout(5000).times(2)).onDeviceConnected(anyString(), any(NetconfSessionPreferences.class),
                any(DOMRpcService.class), isNull());
    }

    @Test
    public void testNetconfDeviceDisconnectListenerCallCancellation() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = mock(EffectiveModelContextFactory.class);
        final SettableFuture<SchemaContext> schemaFuture = SettableFuture.create();
        doReturn(schemaFuture).when(schemaContextProviderFactory).createEffectiveModelContext(any(Collection.class));
        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO
                = new NetconfDevice.SchemaResourcesDTO(getSchemaRegistry(), getSchemaRepository(),
                schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .build();
        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        //session up, start schema resolution
        device.onRemoteSessionUp(sessionCaps, listener);
        //session closed
        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected(anyString());
        //complete schema setup
        schemaFuture.set(SCHEMA_CONTEXT);
        //facade.onDeviceDisconnected() was called, so facade.onDeviceConnected() shouldn't be called anymore
        verify(facade, after(1000).never()).onDeviceConnected(anyString(),
                any(), any(),  any(DOMActionService.class));
    }

    @Test
    public void testNetconfDeviceAvailableCapabilitiesBuilding() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(
                getSchemaRegistry(), getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .build();
        final NetconfDevice netconfSpy = spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        final Map<QName, AvailableCapability.CapabilityOrigin> moduleBasedCaps = new HashMap<>();
        moduleBasedCaps.putAll(sessionCaps.getModuleBasedCapsOrigin());
        moduleBasedCaps
                .put(QName.create("(test:qname:side:loading)test"), AvailableCapability.CapabilityOrigin.UserDefined);

        netconfSpy.onRemoteSessionUp(sessionCaps.replaceModuleCaps(moduleBasedCaps), listener);

        final ArgumentCaptor<NetconfSessionPreferences> argument =
                ArgumentCaptor.forClass(NetconfSessionPreferences.class);
        verify(facade, timeout(5000)).onDeviceConnected(anyString(),
                argument.capture(), any(DOMRpcService.class), isNull());
        final NetconfDeviceCapabilities netconfDeviceCaps = argument.getValue().getNetconfDeviceCapabilities();

        netconfDeviceCaps.getResolvedCapabilities()
                .forEach(entry -> assertEquals("Builded 'AvailableCapability' schemas should match input capabilities.",
                        moduleBasedCaps.get(
                                QName.create(entry.getCapability())).getName(), entry.getCapabilityOrigin().getName()));
    }

    @Test
    public void testNetconfDeviceNotificationsModelNotPresentWithCapability() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(
                getSchemaRegistry(), getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .build();
        final NetconfDevice netconfSpy = spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(false,
                Lists.newArrayList(XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_NOTIFICATION_1_0));

        netconfSpy.onRemoteSessionUp(sessionCaps, listener);

        final ArgumentCaptor<NetconfSessionPreferences> argument =
                ArgumentCaptor.forClass(NetconfSessionPreferences.class);
        verify(facade, timeout(5000)).onDeviceConnected(eq((String)getId().getName()),
                argument.capture(), any(DOMRpcService.class), isNull());

        List<String> notificationModulesName = Arrays.asList(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName().toString(),
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName().toString());

        final Set<AvailableCapability> resolvedCapabilities = argument.getValue().getNetconfDeviceCapabilities()
                .getResolvedCapabilities();

        assertEquals(2, resolvedCapabilities.size());
        assertTrue(resolvedCapabilities.stream().anyMatch(entry -> notificationModulesName
                .contains(entry.getCapability())));
    }

    @Test
    public void testNetconfDeviceNotificationsCapabilityIsNotPresent() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(
                getSchemaRegistry(), getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .build();
        final NetconfDevice netconfSpy = spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(false,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));

        netconfSpy.onRemoteSessionUp(sessionCaps, listener);

        final ArgumentCaptor<NetconfSessionPreferences> argument =
                ArgumentCaptor.forClass(NetconfSessionPreferences.class);
        verify(facade, timeout(5000)).onDeviceConnected(anyString(),
                argument.capture(), any(DOMRpcService.class), isNull());
        final NetconfDeviceCapabilities netconfDeviceCaps = argument.getValue().getNetconfDeviceCapabilities();

        List<String> notificationModulesName = Arrays.asList(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName().toString(),
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName().toString());

        assertFalse(netconfDeviceCaps.getResolvedCapabilities().stream().anyMatch(entry -> notificationModulesName
                .contains(entry.getCapability())));
    }

    @Test
    public void testNetconfDeviceNotificationsModelIsPresent() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final EffectiveModelContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(
                getSchemaRegistry(), getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .setBaseSchemas(BASE_SCHEMAS)
                .setMountPointManager(mountPointManager)
                .build();
        final NetconfDevice netconfSpy = spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(false, Collections.emptyList());

        final Map<QName, AvailableCapability.CapabilityOrigin> moduleBasedCaps = new HashMap<>();
        moduleBasedCaps.put(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName(),
                AvailableCapability.CapabilityOrigin.DeviceAdvertised);
        moduleBasedCaps.put(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName(),
                AvailableCapability.CapabilityOrigin.DeviceAdvertised);


        netconfSpy.onRemoteSessionUp(sessionCaps.replaceModuleCaps(moduleBasedCaps), listener);

        final ArgumentCaptor<NetconfSessionPreferences> argument =
                ArgumentCaptor.forClass(NetconfSessionPreferences.class);
        verify(facade, timeout(5000)).onDeviceConnected(anyString(),
                argument.capture(), any(DOMRpcService.class), isNull());
        final Set<AvailableCapability> resolvedCapabilities = argument.getValue().getNetconfDeviceCapabilities()
                .getResolvedCapabilities();

        List<String> notificationModulesName = Arrays.asList(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName().toString(),
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName().toString());

        assertEquals(2, resolvedCapabilities.size());
        assertTrue(resolvedCapabilities.stream().anyMatch(entry -> notificationModulesName
                .contains(entry.getCapability())));
    }

    private static EffectiveModelContextFactory getSchemaFactory() throws Exception {
        final EffectiveModelContextFactory schemaFactory = mockClass(EffectiveModelContextFactory.class);
        doReturn(Futures.immediateFuture(SCHEMA_CONTEXT))
                .when(schemaFactory).createEffectiveModelContext(any(Collection.class));
        return schemaFactory;
    }

    private  RemoteDeviceHandler<NetconfSessionPreferences> getFacade() throws Exception {
        mountPointManager = mock(MountPointManager.class);
        RemoteDeviceHandler remoteDeviceHandler = mock(RemoteDeviceHandler.class);
        doReturn(remoteDeviceHandler).when(mountPointManager)
                .getInstance(any(), any(), any(), any(), any(), any(), anyLong(), anyLong());
        doReturn(NETCONF_MESSAGE_TRANSFORMER).when(mountPointManager)
                .getNetconfMessageTransformer(any(MountPointContext.class));
        doNothing().when(mountPointManager).updateNetconfMountPointHandler(any(), any(), any(), any());
        doReturn(MOUNT_POINT_CONTEXT).when(mountPointManager).getMountPointContextByNodeId(anyString());
        doNothing().when(remoteDeviceHandler).onDeviceConnected(anyString(), any(NetconfSessionPreferences.class),
                any(), any());
        doNothing().when(remoteDeviceHandler).onDeviceDisconnected(any());
        doNothing().when(remoteDeviceHandler).onNotification(anyString(), any(DOMNotification.class));
        return remoteDeviceHandler;
    }

    private static <T extends AutoCloseable> T mockCloseableClass(final Class<T> remoteDeviceHandlerClass)
            throws Exception {
        final T mock = mockClass(remoteDeviceHandlerClass);
        doNothing().when(mock).close();
        return mock;
    }

    private static <T> T mockClass(final Class<T> remoteDeviceHandlerClass) {
        final T mock = mock(remoteDeviceHandlerClass);
        doReturn(remoteDeviceHandlerClass.getSimpleName()).when(mock).toString();
        return mock;
    }

    public RemoteDeviceId getId() {
        return new RemoteDeviceId("test-D", InetSocketAddress.createUnresolved("localhost", 22));
    }

    public ListeningExecutorService getExecutor() {
        return MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    }

    public MessageTransformer<NetconfMessage> getMessageTransformer() throws Exception {
        final MessageTransformer<NetconfMessage> messageTransformer = mockClass(MessageTransformer.class);
        doReturn(NOTIFICATION).when(messageTransformer).toRpcRequest(any(QName.class), any(NormalizedNode.class));
        doReturn(RPC_RESULT).when(messageTransformer).toRpcResult(any(NetconfMessage.class), any(QName.class));
        doReturn(COMPOSITE_NODE).when(messageTransformer).toNotification(any(NetconfMessage.class));
        return messageTransformer;
    }

    public NetconfSessionPreferences getSessionCaps(final boolean addMonitor,
                                                    final Collection<String> additionalCapabilities) {
        final ArrayList<String> capabilities = Lists.newArrayList(
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);

        if (addMonitor) {
            capabilities.add(NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString());
        }

        capabilities.addAll(additionalCapabilities);

        return NetconfSessionPreferences.fromStrings(
                capabilities);
    }

    public NetconfDeviceCommunicator getListener() throws Exception {
        final NetconfDeviceCommunicator remoteDeviceCommunicator = mockCloseableClass(NetconfDeviceCommunicator.class);
//        doReturn(Futures.immediateFuture(rpcResult))
//                .when(remoteDeviceCommunicator).sendRequest(any(NetconfMessage.class), any(QName.class));
        return remoteDeviceCommunicator;
    }
}

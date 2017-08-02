/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleImport;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.parser.util.ASTSchemaSource;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.xml.sax.SAXException;

@SuppressWarnings("checkstyle:IllegalCatch")
public class NetconfDeviceTest {

    private static final NetconfMessage NOTIFICATION;

    private static final ContainerNode COMPOSITE_NODE;

    static {
        try {
            COMPOSITE_NODE = mockClass(ContainerNode.class);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        try {
            NOTIFICATION = new NetconfMessage(XmlUtil
                    .readXmlToDocument(NetconfDeviceTest.class.getResourceAsStream("/notification-payload.xml")));
        } catch (SAXException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final DOMRpcResult RPC_RESULT = new DefaultDOMRpcResult(COMPOSITE_NODE);

    public static final String TEST_NAMESPACE = "test:namespace";
    public static final String TEST_MODULE = "test-module";
    public static final String TEST_REVISION = "2013-07-22";
    public static final SourceIdentifier TEST_SID =
            RevisionSourceIdentifier.create(TEST_MODULE, Optional.of(TEST_REVISION));
    public static final String TEST_CAPABILITY =
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION;

    public static final SourceIdentifier TEST_SID2 =
            RevisionSourceIdentifier.create(TEST_MODULE + "2", Optional.of(TEST_REVISION));
    public static final String TEST_CAPABILITY2 =
            TEST_NAMESPACE + "?module=" + TEST_MODULE + "2" + "&amp;revision=" + TEST_REVISION;

    private static final NetconfDeviceSchemasResolver STATE_SCHEMAS_RESOLVER =
        (deviceRpc, remoteSessionCapabilities, id) -> NetconfStateSchemas.EMPTY;

    @Test
    public void testNetconfDeviceFlawedModelFailedResolution() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final SchemaContextFactory schemaFactory = getSchemaFactory();
        final SchemaContext schema = getSchema();
        final SchemaRepository schemaRepository = getSchemaRepository();

        final SchemaResolutionException schemaResolutionException =
                new SchemaResolutionException("fail first", TEST_SID, new Throwable("YangTools parser fail"));
        doAnswer(invocation -> {
            if (((Collection<?>) invocation.getArguments()[0]).size() == 2) {
                return Futures.immediateFailedCheckedFuture(schemaResolutionException);
            } else {
                return Futures.immediateCheckedFuture(schema);
            }
        }).when(schemaFactory).createSchemaContext(anyCollectionOf(SourceIdentifier.class));

        final NetconfDeviceSchemasResolver stateSchemasResolver = (deviceRpc, remoteSessionCapabilities, id) -> {
            final Module first = Iterables.getFirst(schema.getModules(), null);
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
                .build();
        // Monitoring supported
        final NetconfSessionPreferences sessionCaps =
                getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY, TEST_CAPABILITY2));
        device.onRemoteSessionUp(sessionCaps, listener);

        Mockito.verify(facade, Mockito.timeout(5000)).onDeviceConnected(
                any(SchemaContext.class), any(NetconfSessionPreferences.class), any(NetconfDeviceRpc.class));
        Mockito.verify(schemaFactory, times(2)).createSchemaContext(anyCollectionOf(SourceIdentifier.class));
    }

    @Test
    public void testNetconfDeviceFailFirstSchemaFailSecondEmpty() throws Exception {
        final ArrayList<String> capList = Lists.newArrayList(TEST_CAPABILITY);

        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final SchemaContextFactory schemaFactory = getSchemaFactory();
        final SchemaRepository schemaRepository = getSchemaRepository();

        // Make fallback attempt to fail due to empty resolved sources
        final SchemaResolutionException schemaResolutionException
                = new SchemaResolutionException("fail first",
                Collections.emptyList(), HashMultimap.create());
        doReturn(Futures.immediateFailedCheckedFuture(
                schemaResolutionException))
                .when(schemaFactory).createSchemaContext(anyCollectionOf(SourceIdentifier.class));

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice
                .SchemaResourcesDTO(getSchemaRegistry(), schemaRepository, schemaFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .build();

        // Monitoring not supported
        final NetconfSessionPreferences sessionCaps = getSessionCaps(false, capList);
        device.onRemoteSessionUp(sessionCaps, listener);

        Mockito.verify(facade, Mockito.timeout(5000)).onDeviceDisconnected();
        Mockito.verify(listener, Mockito.timeout(5000)).close();
        Mockito.verify(schemaFactory, times(1)).createSchemaContext(anyCollectionOf(SourceIdentifier.class));
    }

    @Test
    public void testNetconfDeviceMissingSource() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final SchemaContext schema = getSchema();

        final SchemaContextFactory schemaFactory = getSchemaFactory();
        final SchemaRepository schemaRepository = getSchemaRepository();

        // Make fallback attempt to fail due to empty resolved sources
        final MissingSchemaSourceException schemaResolutionException =
                new MissingSchemaSourceException("fail first", TEST_SID);
        doReturn(Futures.immediateFailedCheckedFuture(schemaResolutionException))
                .when(schemaRepository).getSchemaSource(eq(TEST_SID), eq(ASTSchemaSource.class));
        doAnswer(invocation -> {
            if (((Collection<?>) invocation.getArguments()[0]).size() == 2) {
                return Futures.immediateFailedCheckedFuture(schemaResolutionException);
            } else {
                return Futures.immediateCheckedFuture(schema);
            }
        }).when(schemaFactory).createSchemaContext(anyCollectionOf(SourceIdentifier.class));

        final NetconfDeviceSchemasResolver stateSchemasResolver = (deviceRpc, remoteSessionCapabilities, id) -> {
            final Module first = Iterables.getFirst(schema.getModules(), null);
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
                .build();
        // Monitoring supported
        final NetconfSessionPreferences sessionCaps =
                getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY, TEST_CAPABILITY2));
        device.onRemoteSessionUp(sessionCaps, listener);

        Mockito.verify(facade, Mockito.timeout(5000)).onDeviceConnected(
                any(SchemaContext.class), any(NetconfSessionPreferences.class), any(NetconfDeviceRpc.class));
        Mockito.verify(schemaFactory, times(1)).createSchemaContext(anyCollectionOf(SourceIdentifier.class));
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
        doReturn(Futures.immediateCheckedFuture(mockRep))
                .when(mock).getSchemaSource(any(SourceIdentifier.class), eq(ASTSchemaSource.class));
        return mock;
    }

    @Test
    public void testNotificationBeforeSchema() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final SchemaContextFactory schemaContextProviderFactory = mock(SchemaContextFactory.class);
        final SettableFuture<SchemaContext> schemaFuture = SettableFuture.create();
        doReturn(Futures.makeChecked(schemaFuture, e -> new SchemaResolutionException("fail")))
                .when(schemaContextProviderFactory).createSchemaContext(any(Collection.class));
        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO =
                new NetconfDevice.SchemaResourcesDTO(getSchemaRegistry(), getSchemaRepository(),
                        schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .build();

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_CAPABILITY));
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
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final SchemaContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(
                getSchemaRegistry(), getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .build();
        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(schemaContextProviderFactory, timeout(5000)).createSchemaContext(any(Collection.class));
        verify(facade, timeout(5000)).onDeviceConnected(
                any(SchemaContext.class), any(NetconfSessionPreferences.class), any(DOMRpcService.class));

        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected();

        device.onRemoteSessionUp(sessionCaps, listener);

        verify(schemaContextProviderFactory, timeout(5000).times(2)).createSchemaContext(any(Collection.class));
        verify(facade, timeout(5000).times(2)).onDeviceConnected(
                any(SchemaContext.class), any(NetconfSessionPreferences.class), any(DOMRpcService.class));
    }

    @Test
    public void testNetconfDeviceDisconnectListenerCallCancellation() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();
        final SchemaContextFactory schemaContextProviderFactory = mock(SchemaContextFactory.class);
        final SettableFuture<SchemaContext> schemaFuture = SettableFuture.create();
        doReturn(Futures.makeChecked(schemaFuture, e -> new SchemaResolutionException("fail")))
                .when(schemaContextProviderFactory).createSchemaContext(any(Collection.class));
        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO
                = new NetconfDevice.SchemaResourcesDTO(getSchemaRegistry(), getSchemaRepository(),
                schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .build();
        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        //session up, start schema resolution
        device.onRemoteSessionUp(sessionCaps, listener);
        //session closed
        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected();
        //complete schema setup
        schemaFuture.set(getSchema());
        //facade.onDeviceDisconnected() was called, so facade.onDeviceConnected() shouldn't be called anymore
        verify(facade, after(1000).never()).onDeviceConnected(any(), any(), any());
    }

    @Test
    public void testNetconfDeviceAvailableCapabilitiesBuilding() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = getListener();

        final SchemaContextFactory schemaContextProviderFactory = getSchemaFactory();

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(
                getSchemaRegistry(), getSchemaRepository(), schemaContextProviderFactory, STATE_SCHEMAS_RESOLVER);
        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(true)
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(getExecutor())
                .setId(getId())
                .setSalFacade(facade)
                .build();
        final NetconfDevice netconfSpy = Mockito.spy(device);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        final Map<QName, AvailableCapability.CapabilityOrigin> moduleBasedCaps = new HashMap<>();
        moduleBasedCaps.putAll(sessionCaps.getModuleBasedCapsOrigin());
        moduleBasedCaps
                .put(QName.create("(test:qname:side:loading)test"), AvailableCapability.CapabilityOrigin.UserDefined);

        netconfSpy.onRemoteSessionUp(sessionCaps.replaceModuleCaps(moduleBasedCaps), listener);

        final ArgumentCaptor<NetconfSessionPreferences> argument =
                ArgumentCaptor.forClass(NetconfSessionPreferences.class);
        verify(facade, timeout(5000))
                .onDeviceConnected(any(SchemaContext.class), argument.capture(), any(DOMRpcService.class));
        final NetconfDeviceCapabilities netconfDeviceCaps = argument.getValue().getNetconfDeviceCapabilities();

        netconfDeviceCaps.getResolvedCapabilities()
                .forEach(entry -> assertEquals("Builded 'AvailableCapability' schemas should match input capabilities.",
                        moduleBasedCaps.get(
                                QName.create(entry.getCapability())).getName(), entry.getCapabilityOrigin().getName()));
    }

    private static SchemaContextFactory getSchemaFactory() throws Exception {
        final SchemaContextFactory schemaFactory = mockClass(SchemaContextFactory.class);
        doReturn(Futures.immediateCheckedFuture(getSchema()))
                .when(schemaFactory).createSchemaContext(any(Collection.class));
        return schemaFactory;
    }

    public static SchemaContext getSchema() throws Exception {
        final List<InputStream> modelsToParse = Lists.newArrayList(
                NetconfDeviceTest.class.getResourceAsStream("/schemas/test-module.yang")
        );
        return YangParserTestUtils.parseYangStreams(modelsToParse);
    }

    private static RemoteDeviceHandler<NetconfSessionPreferences> getFacade() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> remoteDeviceHandler =
                mockCloseableClass(RemoteDeviceHandler.class);
        doNothing().when(remoteDeviceHandler).onDeviceConnected(
                any(SchemaContext.class), any(NetconfSessionPreferences.class), any(NetconfDeviceRpc.class));
        doNothing().when(remoteDeviceHandler).onDeviceDisconnected();
        doNothing().when(remoteDeviceHandler).onNotification(any(DOMNotification.class));
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
        Mockito.doReturn(remoteDeviceHandlerClass.getSimpleName()).when(mock).toString();
        return mock;
    }

    public RemoteDeviceId getId() {
        return new RemoteDeviceId("test-D", InetSocketAddress.createUnresolved("localhost", 22));
    }

    public ExecutorService getExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    public MessageTransformer<NetconfMessage> getMessageTransformer() throws Exception {
        final MessageTransformer<NetconfMessage> messageTransformer = mockClass(MessageTransformer.class);
        doReturn(NOTIFICATION).when(messageTransformer).toRpcRequest(any(SchemaPath.class), any(NormalizedNode.class));
        doReturn(RPC_RESULT).when(messageTransformer).toRpcResult(any(NetconfMessage.class), any(SchemaPath.class));
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

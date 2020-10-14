/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.io.Resources;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.TestUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.ActionServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapper;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Unit tests for JSONRestconfServiceDraft18.
 *
 * @author Thomas Pantelis
 */
public class JSONRestconfServiceRfc8040ImplTest {
    static final String IETF_INTERFACES_NS = "urn:ietf:params:xml:ns:yang:ietf-interfaces";
    static final String IETF_INTERFACES_VERSION = "2013-07-04";
    static final QName INTERFACES_QNAME = QName.create(IETF_INTERFACES_NS, IETF_INTERFACES_VERSION, "interfaces");
    static final QName INTERFACE_QNAME = QName.create(IETF_INTERFACES_NS, IETF_INTERFACES_VERSION, "interface");
    static final QName NAME_QNAME = QName.create(IETF_INTERFACES_NS, IETF_INTERFACES_VERSION, "name");
    static final QName TYPE_QNAME = QName.create(IETF_INTERFACES_NS, IETF_INTERFACES_VERSION, "type");
    static final QName ENABLED_QNAME = QName.create(IETF_INTERFACES_NS, IETF_INTERFACES_VERSION, "enabled");
    static final QName DESC_QNAME = QName.create(IETF_INTERFACES_NS, IETF_INTERFACES_VERSION, "description");

    static final String TEST_MODULE_NS = "test:module";
    static final String TEST_MODULE_VERSION = "2014-01-09";
    static final QName TEST_CONT_QNAME = QName.create(TEST_MODULE_NS, TEST_MODULE_VERSION, "cont");
    static final QName TEST_CONT1_QNAME = QName.create(TEST_MODULE_NS, TEST_MODULE_VERSION, "cont1");
    static final QName TEST_LF11_QNAME = QName.create(TEST_MODULE_NS, TEST_MODULE_VERSION, "lf11");
    static final QName TEST_LF12_QNAME = QName.create(TEST_MODULE_NS, TEST_MODULE_VERSION, "lf12");

    static final String TOASTER_MODULE_NS = "http://netconfcentral.org/ns/toaster";
    static final String TOASTER_MODULE_VERSION = "2009-11-20";
    static final QName TOASTER_DONENESS_QNAME =
            QName.create(TOASTER_MODULE_NS, TOASTER_MODULE_VERSION, "toasterDoneness");
    static final QName TOASTER_TYPE_QNAME = QName.create(TOASTER_MODULE_NS, TOASTER_MODULE_VERSION, "toasterToastType");
    static final QName WHEAT_BREAD_QNAME = QName.create(TOASTER_MODULE_NS, TOASTER_MODULE_VERSION, "wheat-bread");
    static final QName MAKE_TOAST_QNAME = QName.create(TOASTER_MODULE_NS, TOASTER_MODULE_VERSION, "make-toast");
    static final QName CANCEL_TOAST_QNAME = QName.create(TOASTER_MODULE_NS, TOASTER_MODULE_VERSION, "cancel-toast");
    static final QName TEST_OUTPUT_QNAME = QName.create(TOASTER_MODULE_NS, TOASTER_MODULE_VERSION, "testOutput");
    static final QName TEXT_OUT_QNAME = QName.create(TOASTER_MODULE_NS, TOASTER_MODULE_VERSION, "textOut");

    private static EffectiveModelContext schemaContext;

    @Mock
    private DOMTransactionChain mockTxChain;

    @Mock
    private DOMDataTreeReadWriteTransaction mockReadWriteTx;

    @Mock
    private DOMDataTreeReadTransaction mockReadOnlyTx;

    @Mock
    private DOMDataTreeWriteTransaction mockWriteTx;

    @Mock
    private DOMMountPointService mockMountPointService;

    @Mock
    private DOMDataBroker mockDOMDataBroker;

    @Mock
    private DOMRpcService mockRpcService;

    @Mock
    private DOMActionService mockActionService;

    @Mock
    private DOMSchemaService domSchemaService;

    @Mock
    private Configuration configuration;

    private JSONRestconfServiceRfc8040Impl service;

    private final SchemaContextHandler schemaContextHandler = TestUtils.newSchemaContextHandler(schemaContext);

    @BeforeClass
    public static void init() throws IOException {
        schemaContext = TestUtils.loadSchemaContext("/full-versions/yangs");
    }

    @SuppressWarnings("resource")
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(ImmutableClassToInstanceMap.of()).when(domSchemaService).getExtensions();

        doReturn(immediateFluentFuture(Optional.empty())).when(mockReadOnlyTx).read(
                eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        doNothing().when(mockWriteTx).put(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class),
                any(NormalizedNode.class));
        doNothing().when(mockWriteTx).merge(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class),
                any(NormalizedNode.class));
        doNothing().when(mockWriteTx).delete(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        doReturn(CommitInfo.emptyFluentFuture()).when(mockWriteTx).commit();

        doNothing().when(mockReadWriteTx).put(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class),
                any(NormalizedNode.class));
        doReturn(CommitInfo.emptyFluentFuture()).when(mockReadWriteTx).commit();
        doReturn(immediateFluentFuture(Optional.empty())).when(mockReadWriteTx).read(
                eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        doReturn(immediateFalseFluentFuture()).when(mockReadOnlyTx).exists(
                eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        doReturn(mockReadOnlyTx).when(mockTxChain).newReadOnlyTransaction();
        doReturn(mockReadWriteTx).when(mockTxChain).newReadWriteTransaction();
        doReturn(mockWriteTx).when(mockTxChain).newWriteOnlyTransaction();

        doReturn(mockTxChain).when(mockDOMDataBroker).createTransactionChain(any());

        final TransactionChainHandler txChainHandler = new TransactionChainHandler(mockDOMDataBroker);

        final DOMMountPointServiceHandler mountPointServiceHandler =
                new DOMMountPointServiceHandler(mockMountPointService);

        final DOMNotificationService mockNotificationService = mock(DOMNotificationService.class);
        final ServicesWrapper servicesWrapper = ServicesWrapper.newInstance(schemaContextHandler,
                mountPointServiceHandler, txChainHandler, new DOMDataBrokerHandler(mockDOMDataBroker),
                new RpcServiceHandler(mockRpcService), new ActionServiceHandler(mockActionService),
                new NotificationServiceHandler(mockNotificationService), domSchemaService, configuration);

        service = new JSONRestconfServiceRfc8040Impl(servicesWrapper, mountPointServiceHandler,
                schemaContextHandler);
    }

    private static String loadData(final String path) throws IOException {
        return Resources.asCharSource(JSONRestconfServiceRfc8040ImplTest.class.getResource(path),
                StandardCharsets.UTF_8).read();
    }

    @Test
    public void testPut() throws Exception {
        final String uriPath = "ietf-interfaces:interfaces/interface=eth0";
        final String payload = loadData("/parts/ietf-interfaces_interfaces.json");

        this.service.put(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode<?, ?>> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);

        verify(mockReadWriteTx).put(eq(LogicalDatastoreType.CONFIGURATION), capturedPath.capture(),
                capturedNode.capture());

        verifyPath(capturedPath.getValue(), INTERFACES_QNAME, INTERFACE_QNAME,
                new Object[]{INTERFACE_QNAME, NAME_QNAME, "eth0"});

        assertTrue("Expected MapEntryNode. Actual " + capturedNode.getValue().getClass(),
                capturedNode.getValue() instanceof MapEntryNode);
        final MapEntryNode actualNode = (MapEntryNode) capturedNode.getValue();
        assertEquals("MapEntryNode node type", INTERFACE_QNAME, actualNode.getNodeType());
        verifyLeafNode(actualNode, NAME_QNAME, "eth0");
        verifyLeafNode(actualNode, TYPE_QNAME, "ethernetCsmacd");
        verifyLeafNode(actualNode, ENABLED_QNAME, Boolean.FALSE);
        verifyLeafNode(actualNode, DESC_QNAME, "some interface");
    }

    @Test
    public void testPutBehindMountPoint() throws Exception {
        setupTestMountPoint();

        final String uriPath = "ietf-interfaces:interfaces/yang-ext:mount/test-module:cont/cont1";
        final String payload = loadData("/full-versions/testCont1Data.json");

        this.service.put(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode<?, ?>> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);

        verify(mockReadWriteTx).put(eq(LogicalDatastoreType.CONFIGURATION), capturedPath.capture(),
                capturedNode.capture());

        verifyPath(capturedPath.getValue(), TEST_CONT_QNAME, TEST_CONT1_QNAME);

        assertTrue("Expected ContainerNode", capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        assertEquals("ContainerNode node type", TEST_CONT1_QNAME, actualNode.getNodeType());
        verifyLeafNode(actualNode, TEST_LF11_QNAME, "lf11 data");
        verifyLeafNode(actualNode, TEST_LF12_QNAME, "lf12 data");
    }

    public void testPutFailure() throws IOException {
        doReturn(immediateFailedFluentFuture(new TransactionCommitFailedException("mock")))
                .when(mockReadWriteTx).commit();

        final String uriPath = "ietf-interfaces:interfaces/interface=eth0";
        final String payload = loadData("/parts/ietf-interfaces_interfaces.json");

        assertThrows(OperationFailedException.class, () -> this.service.put(uriPath, payload));
    }

    @Test
    public void testPost() throws Exception {
        final String uriPath = null;
        final String payload = loadData("/parts/ietf-interfaces_interfaces_absolute_path.json");

        this.service.post(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode<?, ?>> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);

        verify(mockReadWriteTx).put(eq(LogicalDatastoreType.CONFIGURATION), capturedPath.capture(),
                capturedNode.capture());

        verifyPath(capturedPath.getValue(), INTERFACES_QNAME);

        assertTrue("Expected ContainerNode", capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        assertEquals("ContainerNode node type", INTERFACES_QNAME, actualNode.getNodeType());

        final Optional<DataContainerChild<?, ?>> mapChild = actualNode.getChild(new NodeIdentifier(INTERFACE_QNAME));
        assertEquals(INTERFACE_QNAME.toString() + " present", true, mapChild.isPresent());
        assertTrue("Expected MapNode. Actual " + mapChild.get().getClass(), mapChild.get() instanceof MapNode);
        final MapNode mapNode = (MapNode)mapChild.get();

        final NodeIdentifierWithPredicates entryNodeID = NodeIdentifierWithPredicates.of(
                INTERFACE_QNAME, NAME_QNAME, "eth0");
        final Optional<MapEntryNode> entryChild = mapNode.getChild(entryNodeID);
        assertEquals(entryNodeID.toString() + " present", true, entryChild.isPresent());
        final MapEntryNode entryNode = entryChild.get();
        verifyLeafNode(entryNode, NAME_QNAME, "eth0");
        verifyLeafNode(entryNode, TYPE_QNAME, "ethernetCsmacd");
        verifyLeafNode(entryNode, ENABLED_QNAME, Boolean.FALSE);
        verifyLeafNode(entryNode, DESC_QNAME, "some interface");
    }

    @Test
    public void testPostBehindMountPoint() throws Exception {
        setupTestMountPoint();

        final String uriPath = "ietf-interfaces:interfaces/yang-ext:mount/test-module:cont";
        final String payload = loadData("/full-versions/testCont1Data.json");

        this.service.post(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode<?, ?>> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);

        verify(mockReadWriteTx).put(eq(LogicalDatastoreType.CONFIGURATION), capturedPath.capture(),
                capturedNode.capture());

        verifyPath(capturedPath.getValue(), TEST_CONT_QNAME, TEST_CONT1_QNAME);

        assertTrue("Expected ContainerNode", capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        assertEquals("ContainerNode node type", TEST_CONT1_QNAME, actualNode.getNodeType());
        verifyLeafNode(actualNode, TEST_LF11_QNAME, "lf11 data");
        verifyLeafNode(actualNode, TEST_LF12_QNAME, "lf12 data");
    }

    @Test
    public void testPostFailure() throws IOException {
        final Exception failure = new TransactionCommitFailedException("mock");
        doReturn(immediateFailedFluentFuture(failure)).when(mockReadWriteTx).commit();

        final String payload = loadData("/parts/ietf-interfaces_interfaces_absolute_path.json");
        try {
            this.service.post(null, payload);
            fail();
        } catch (final OperationFailedException e) {
            final Throwable cause = e.getCause();
            assertNotNull(cause);
            assertSame(failure, cause.getCause());
        }
    }

    @Test
    public void testPatch() throws Exception {
        final String uriPath = "ietf-interfaces:interfaces/interface=eth0";
        final String payload = loadData("/parts/ietf-interfaces_interfaces_patch.json");

        final Optional<String> patchResult = this.service.patch(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode<?, ?>> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);

        verify(mockReadWriteTx).put(eq(LogicalDatastoreType.CONFIGURATION), capturedPath.capture(),
                capturedNode.capture());

        verifyPath(capturedPath.getValue(), INTERFACES_QNAME, INTERFACE_QNAME,
                new Object[]{INTERFACE_QNAME, NAME_QNAME, "eth0"});

        assertTrue("Expected MapEntryNode. Actual " + capturedNode.getValue().getClass(),
                capturedNode.getValue() instanceof MapEntryNode);
        final MapEntryNode actualNode = (MapEntryNode) capturedNode.getValue();
        assertEquals("MapEntryNode node type", INTERFACE_QNAME, actualNode.getNodeType());
        verifyLeafNode(actualNode, NAME_QNAME, "eth0");
        verifyLeafNode(actualNode, TYPE_QNAME, "ethernetCsmacd");
        verifyLeafNode(actualNode, ENABLED_QNAME, Boolean.FALSE);
        verifyLeafNode(actualNode, DESC_QNAME, "some interface");
        assertTrue(patchResult.get().contains("\"ok\":[null]"));
    }

    @Test
    public void testPatchBehindMountPoint() throws Exception {
        setupTestMountPoint();

        final String uriPath = "ietf-interfaces:interfaces/yang-ext:mount/test-module:cont/cont1";
        final String payload = loadData("/full-versions/testCont1DataPatch.json");

        final Optional<String> patchResult = this.service.patch(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode<?, ?>> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);

        verify(mockReadWriteTx).put(eq(LogicalDatastoreType.CONFIGURATION), capturedPath.capture(),
                capturedNode.capture());

        verifyPath(capturedPath.getValue(), TEST_CONT_QNAME, TEST_CONT1_QNAME);

        assertTrue("Expected ContainerNode", capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        assertEquals("ContainerNode node type", TEST_CONT1_QNAME, actualNode.getNodeType());
        verifyLeafNode(actualNode, TEST_LF11_QNAME, "lf11 data");
        verifyLeafNode(actualNode, TEST_LF12_QNAME, "lf12 data");
        assertTrue(patchResult.get().contains("\"ok\":[null]"));
    }

    @Test
    public void testPatchFailure() throws IOException, OperationFailedException {
        doReturn(immediateFailedFluentFuture(new TransactionCommitFailedException("mock")))
                .when(mockReadWriteTx).commit();

        final String uriPath = "ietf-interfaces:interfaces/interface=eth0";

        final String payload = loadData("/parts/ietf-interfaces_interfaces_patch.json");

        final Optional<String> patchResult = this.service.patch(uriPath, payload);
        assertTrue("Patch output is not null", patchResult.isPresent());
        String patch = patchResult.get();
        assertTrue(patch.contains("mock"));
    }

    @Test
    public void testDelete() throws OperationFailedException {
        doReturn(immediateTrueFluentFuture()).when(mockReadOnlyTx).exists(
                eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        final String uriPath = "ietf-interfaces:interfaces/interface=eth0";

        this.service.delete(uriPath);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);

        verify(mockReadWriteTx).delete(eq(LogicalDatastoreType.CONFIGURATION), capturedPath.capture());

        verifyPath(capturedPath.getValue(), INTERFACES_QNAME, INTERFACE_QNAME,
                new Object[]{INTERFACE_QNAME, NAME_QNAME, "eth0"});
    }

    public void testDeleteFailure() {
        assertThrows(OperationFailedException.class, () -> this.service.delete("ietf-interfaces:interfaces/invalid"));
    }

    @Test
    public void testGetConfig() throws OperationFailedException {
        testGet(LogicalDatastoreType.CONFIGURATION);
    }

    @Test
    public void testGetOperational() throws OperationFailedException {
        testGet(LogicalDatastoreType.OPERATIONAL);
    }

    @Test
    public void testGetWithNoData() throws OperationFailedException {
        final String uriPath = "ietf-interfaces:interfaces";
        this.service.get(uriPath, LogicalDatastoreType.CONFIGURATION);
    }

    public void testGetFailure() {
        assertThrows(OperationFailedException.class,
            () -> this.service.get("/ietf-interfaces:interfaces/invalid", LogicalDatastoreType.CONFIGURATION));
    }

    @Test
    public void testInvokeRpcWithInput() throws IOException, OperationFailedException {
        final DOMRpcResult expResult = new DefaultDOMRpcResult((NormalizedNode<?, ?>)null);
        doReturn(immediateFluentFuture(expResult)).when(mockRpcService).invokeRpc(eq(MAKE_TOAST_QNAME),
            any(NormalizedNode.class));

        final String uriPath = "toaster:make-toast";
        final String input = loadData("/full-versions/make-toast-rpc-input.json");

        final Optional<String> output = this.service.invokeRpc(uriPath, Optional.of(input));

        assertEquals("Output present", false, output.isPresent());

        final ArgumentCaptor<NormalizedNode<?, ?>> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);
        verify(mockRpcService).invokeRpc(eq(MAKE_TOAST_QNAME), capturedNode.capture());

        assertTrue("Expected ContainerNode. Actual " + capturedNode.getValue().getClass(),
                capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        verifyLeafNode(actualNode, TOASTER_DONENESS_QNAME, Uint32.valueOf(10L));
        verifyLeafNode(actualNode, TOASTER_TYPE_QNAME, WHEAT_BREAD_QNAME);
    }

    @Test
    public void testInvokeRpcWithNoInput() throws OperationFailedException {
        final DOMRpcResult expResult = new DefaultDOMRpcResult((NormalizedNode<?, ?>)null);
        doReturn(immediateFluentFuture(expResult)).when(mockRpcService).invokeRpc(eq(CANCEL_TOAST_QNAME), any());

        final String uriPath = "toaster:cancel-toast";

        final Optional<String> output = this.service.invokeRpc(uriPath, Optional.empty());

        assertEquals("Output present", false, output.isPresent());

        verify(mockRpcService).invokeRpc(eq(CANCEL_TOAST_QNAME), any());
    }

    @Test
    public void testInvokeRpcWithOutput() throws OperationFailedException {
        final NormalizedNode<?, ?> outputNode = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(TEST_OUTPUT_QNAME))
                .withChild(ImmutableNodes.leafNode(TEXT_OUT_QNAME, "foo")).build();
        final DOMRpcResult expResult = new DefaultDOMRpcResult(outputNode);
        doReturn(immediateFluentFuture(expResult)).when(mockRpcService).invokeRpc(eq(TEST_OUTPUT_QNAME), any());

        final String uriPath = "toaster:testOutput";

        final Optional<String> output = this.service.invokeRpc(uriPath, Optional.empty());

        assertEquals("Output present", true, output.isPresent());
        assertNotNull("Returned null response", output.get());
        assertThat("Output element is missing namespace", output.get(), containsString("\"toaster:output\""));
        assertThat("Missing \"textOut\"", output.get(), containsString("\"textOut\":\"foo\""));

        verify(mockRpcService).invokeRpc(eq(TEST_OUTPUT_QNAME), any());
    }

    public void testInvokeRpcFailure() {
        final DOMRpcException exception = new DOMRpcImplementationNotAvailableException("testExeption");
        doReturn(immediateFailedFluentFuture(exception)).when(mockRpcService).invokeRpc(any(QName.class),
                any(NormalizedNode.class));

        assertThrows(OperationFailedException.class,
            () -> this.service.invokeRpc("toaster:cancel-toast", Optional.empty()));
    }

    void testGet(final LogicalDatastoreType datastoreType) throws OperationFailedException {
        final MapEntryNode entryNode = ImmutableNodes.mapEntryBuilder(INTERFACE_QNAME, NAME_QNAME, "eth0")
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "eth0"))
                .withChild(ImmutableNodes.leafNode(TYPE_QNAME, "ethernetCsmacd"))
                .withChild(ImmutableNodes.leafNode(ENABLED_QNAME, Boolean.TRUE))
                .withChild(ImmutableNodes.leafNode(DESC_QNAME, "eth interface"))
                .build();

        doReturn(immediateFluentFuture(Optional.of(entryNode))).when(mockReadOnlyTx).read(
                eq(datastoreType), any(YangInstanceIdentifier.class));

        final String uriPath = "ietf-interfaces:interfaces/interface=eth0";

        final Optional<String> optionalResp = this.service.get(uriPath, datastoreType);
        assertEquals("Response present", true, optionalResp.isPresent());
        final String jsonResp = optionalResp.get();

        assertNotNull("Returned null response", jsonResp);
        assertThat("Top level module has incorrect format", jsonResp, containsString("\"ietf-interfaces:interface\""));
        assertThat("Missing \"name\"", jsonResp, containsString("\"name\":\"eth0\""));
        assertThat("Missing \"type\"", jsonResp, containsString("\"type\":\"ethernetCsmacd\""));
        assertThat("Missing \"enabled\"", jsonResp, containsString("\"enabled\":true"));
        assertThat("Missing \"description\"", jsonResp, containsString("\"description\":\"eth interface\""));

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        verify(mockReadOnlyTx).read(eq(datastoreType), capturedPath.capture());

        verifyPath(capturedPath.getValue(), INTERFACES_QNAME, INTERFACE_QNAME,
                new Object[]{INTERFACE_QNAME, NAME_QNAME, "eth0"});
    }

    DOMMountPoint setupTestMountPoint() throws FileNotFoundException {
        final EffectiveModelContext schemaContextTestModule = TestUtils.loadSchemaContext("/full-versions/test-module");
        final DOMMountPoint mockMountPoint = mock(DOMMountPoint.class);
        doReturn(Optional.of(FixedDOMSchemaService.of(schemaContextTestModule))).when(mockMountPoint)
            .getService(DOMSchemaService.class);

        doReturn(Optional.of(mockDOMDataBroker)).when(mockMountPoint).getService(DOMDataBroker.class);

        doReturn(Optional.of(mockMountPoint)).when(mockMountPointService).getMountPoint(notNull());

        return mockMountPoint;
    }

    void verifyLeafNode(final DataContainerNode<?> parent, final QName leafType, final Object leafValue) {
        final Optional<DataContainerChild<?, ?>> leafChild = parent.getChild(new NodeIdentifier(leafType));
        assertEquals(leafType.toString() + " present", true, leafChild.isPresent());
        assertEquals(leafType.toString() + " value", leafValue, leafChild.get().getValue());
    }

    void verifyPath(final YangInstanceIdentifier path, final Object... expArgs) {
        final List<PathArgument> pathArgs = path.getPathArguments();
        assertEquals("Arg count for actual path " + path, expArgs.length, pathArgs.size());
        int index = 0;
        for (final PathArgument actual: pathArgs) {
            QName expNodeType;
            if (expArgs[index] instanceof Object[]) {
                final Object[] listEntry = (Object[]) expArgs[index];
                expNodeType = (QName) listEntry[0];

                assertTrue(actual instanceof NodeIdentifierWithPredicates);
                final NodeIdentifierWithPredicates nip = (NodeIdentifierWithPredicates)actual;

                assertEquals(String.format("Path arg %d keyValues size", index + 1), 1, nip.size());
                final QName expKey = (QName) listEntry[1];
                assertEquals(String.format("Path arg %d keyValue for %s", index + 1, expKey), listEntry[2],
                    nip.getValue(expKey));
            } else {
                expNodeType = (QName) expArgs[index];
            }

            assertEquals(String.format("Path arg %d node type", index + 1), expNodeType, actual.getNodeType());
            index++;
        }

    }
}

/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.base.Optional;
import com.google.common.io.Resources;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response.Status;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.JSONRestconfServiceImpl;
import org.opendaylight.netconf.sal.restconf.impl.PutResult;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.QName;
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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

/**
 * Unit tests for JSONRestconfServiceImpl.
 *
 * @author Thomas Pantelis
 */
@Deprecated
public class JSONRestconfServiceImplTest {
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

    private static SchemaContext schemaContext;

    private final BrokerFacade brokerFacade = mock(BrokerFacade.class);
    private final DOMMountPoint mockMountPoint = mock(DOMMountPoint.class);
    private JSONRestconfServiceImpl service;

    @BeforeClass
    public static void init() throws IOException, ReactorException {
        schemaContext = TestUtils.loadSchemaContext("/full-versions/yangs");
    }

    @Before
    public void setup() throws FileNotFoundException {
        final SchemaContext mountPointSchemaContext = TestUtils.loadSchemaContext("/full-versions/test-module");
        final ControllerContext controllerContext =
                TestRestconfUtils.newControllerContext(schemaContext, mockMountPoint);
        doReturn(mountPointSchemaContext).when(mockMountPoint).getSchemaContext();

        service = new JSONRestconfServiceImpl(controllerContext,
                RestconfImpl.newInstance(brokerFacade, controllerContext));
    }

    private static String loadData(final String path) throws IOException {
        return Resources.asCharSource(JSONRestconfServiceImplTest.class.getResource(path),
                StandardCharsets.UTF_8).read();
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testPut() throws Exception {
        final PutResult result = mock(PutResult.class);
        when(brokerFacade.commitConfigurationDataPut(notNull(SchemaContext.class),
                notNull(YangInstanceIdentifier.class), notNull(NormalizedNode.class), isNull(), isNull()))
                .thenReturn(result);
        doReturn(CommitInfo.emptyFluentFuture()).when(result).getFutureOfPutData();
        when(result.getStatus()).thenReturn(Status.OK);
        final String uriPath = "ietf-interfaces:interfaces/interface/eth0";
        final String payload = loadData("/parts/ietf-interfaces_interfaces.json");
        this.service.put(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);
        verify(brokerFacade).commitConfigurationDataPut(notNull(SchemaContext.class), capturedPath.capture(),
                capturedNode.capture(), isNull(), isNull());

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

    @SuppressWarnings("rawtypes")
    @Test
    public void testPutBehindMountPoint() throws Exception {
        final PutResult result = mock(PutResult.class);
        when(brokerFacade.commitMountPointDataPut(notNull(DOMMountPoint.class),
                notNull(YangInstanceIdentifier.class), notNull(NormalizedNode.class), isNull(), isNull()))
                .thenReturn(result);
        doReturn(CommitInfo.emptyFluentFuture()).when(result).getFutureOfPutData();
        when(result.getStatus()).thenReturn(Status.OK);
        final String uriPath = "ietf-interfaces:interfaces/yang-ext:mount/test-module:cont/cont1";
        final String payload = loadData("/full-versions/testCont1Data.json");

        this.service.put(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);
        verify(brokerFacade).commitMountPointDataPut(same(mockMountPoint), capturedPath.capture(),
                capturedNode.capture(), isNull(), isNull());

        verifyPath(capturedPath.getValue(), TEST_CONT_QNAME, TEST_CONT1_QNAME);

        assertTrue("Expected ContainerNode", capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        assertEquals("ContainerNode node type", TEST_CONT1_QNAME, actualNode.getNodeType());
        verifyLeafNode(actualNode, TEST_LF11_QNAME, "lf11 data");
        verifyLeafNode(actualNode, TEST_LF12_QNAME, "lf12 data");
    }

    @Test(expected = OperationFailedException.class)
    @SuppressWarnings("checkstyle:IllegalThrows")
    public void testPutFailure() throws Throwable {
        final PutResult result = mock(PutResult.class);

        doReturn(immediateFailedFluentFuture(new TransactionCommitFailedException("mock"))).when(result)
        .getFutureOfPutData();
        when(result.getStatus()).thenReturn(Status.OK);
        when(brokerFacade.commitConfigurationDataPut(notNull(SchemaContext.class),
                notNull(YangInstanceIdentifier.class), notNull(NormalizedNode.class), Mockito.anyString(),
                Mockito.anyString())).thenReturn(result);

        final String uriPath = "ietf-interfaces:interfaces/interface/eth0";
        final String payload = loadData("/parts/ietf-interfaces_interfaces.json");

        this.service.put(uriPath, payload);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testPost() throws Exception {
        doReturn(CommitInfo.emptyFluentFuture()).when(brokerFacade).commitConfigurationDataPost(
                any(SchemaContext.class), any(YangInstanceIdentifier.class), any(NormalizedNode.class),
                isNull(), isNull());

        final String uriPath = null;
        final String payload = loadData("/parts/ietf-interfaces_interfaces_absolute_path.json");

        this.service.post(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);
        verify(brokerFacade).commitConfigurationDataPost(notNull(SchemaContext.class), capturedPath.capture(),
                capturedNode.capture(), isNull(), isNull());

        verifyPath(capturedPath.getValue(), INTERFACES_QNAME);

        assertTrue("Expected ContainerNode", capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        assertEquals("ContainerNode node type", INTERFACES_QNAME, actualNode.getNodeType());

        final java.util.Optional<DataContainerChild<?, ?>> mapChild = actualNode.getChild(
            new NodeIdentifier(INTERFACE_QNAME));
        assertEquals(INTERFACE_QNAME.toString() + " present", true, mapChild.isPresent());
        assertTrue("Expected MapNode. Actual " + mapChild.get().getClass(), mapChild.get() instanceof MapNode);
        final MapNode mapNode = (MapNode)mapChild.get();

        final NodeIdentifierWithPredicates entryNodeID = new NodeIdentifierWithPredicates(
                INTERFACE_QNAME, NAME_QNAME, "eth0");
        final java.util.Optional<MapEntryNode> entryChild = mapNode.getChild(entryNodeID);
        assertEquals(entryNodeID.toString() + " present", true, entryChild.isPresent());
        final MapEntryNode entryNode = entryChild.get();
        verifyLeafNode(entryNode, NAME_QNAME, "eth0");
        verifyLeafNode(entryNode, TYPE_QNAME, "ethernetCsmacd");
        verifyLeafNode(entryNode, ENABLED_QNAME, Boolean.FALSE);
        verifyLeafNode(entryNode, DESC_QNAME, "some interface");
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testPostBehindMountPoint() throws Exception {
        doReturn(CommitInfo.emptyFluentFuture()).when(brokerFacade).commitConfigurationDataPost(
                notNull(DOMMountPoint.class), notNull(YangInstanceIdentifier.class), notNull(NormalizedNode.class),
                isNull(), isNull());

        final String uriPath = "ietf-interfaces:interfaces/yang-ext:mount/test-module:cont";
        final String payload = loadData("/full-versions/testCont1Data.json");

        this.service.post(uriPath, payload);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        final ArgumentCaptor<NormalizedNode> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);
        verify(brokerFacade).commitConfigurationDataPost(same(mockMountPoint), capturedPath.capture(),
                capturedNode.capture(), isNull(), isNull());

        verifyPath(capturedPath.getValue(), TEST_CONT_QNAME, TEST_CONT1_QNAME);

        assertTrue("Expected ContainerNode", capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        assertEquals("ContainerNode node type", TEST_CONT1_QNAME, actualNode.getNodeType());
        verifyLeafNode(actualNode, TEST_LF11_QNAME, "lf11 data");
        verifyLeafNode(actualNode, TEST_LF12_QNAME, "lf12 data");
    }

    @Test(expected = TransactionCommitFailedException.class)
    @SuppressWarnings({ "checkstyle:IllegalThrows", "checkstyle:avoidHidingCauseException" })
    public void testPostFailure() throws Throwable {
        doReturn(immediateFailedFluentFuture(new TransactionCommitFailedException("mock"))).when(brokerFacade)
                .commitConfigurationDataPost(any(SchemaContext.class), any(YangInstanceIdentifier.class),
                        any(NormalizedNode.class), isNull(), isNull());

        final String uriPath = null;
        final String payload = loadData("/parts/ietf-interfaces_interfaces_absolute_path.json");

        try {
            this.service.post(uriPath, payload);
        } catch (final OperationFailedException e) {
            assertNotNull(e.getCause());
            throw e.getCause();
        }
    }

    @Test
    public void testPatch() throws Exception {
        final PatchStatusContext result = mock(PatchStatusContext.class);
        when(brokerFacade.patchConfigurationDataWithinTransaction(notNull(PatchContext.class)))
            .thenReturn(result);

        List<PatchStatusEntity> patchSTatus = new ArrayList<>();

        PatchStatusEntity entity = new PatchStatusEntity("edit1", true, null);

        patchSTatus.add(entity);

        when(result.getEditCollection())
                .thenReturn(patchSTatus);
        when(result.getGlobalErrors()).thenReturn(new ArrayList<>());
        when(result.getPatchId()).thenReturn("1");
        final String uriPath = "ietf-interfaces:interfaces/interface/eth0";
        final String payload = loadData("/parts/ietf-interfaces_interfaces_patch.json");
        final Optional<String> patchResult = this.service.patch(uriPath, payload);

        assertTrue(patchResult.get().contains("\"ok\":[null]"));
    }

    @Test
    public void testPatchBehindMountPoint() throws Exception {
        final PatchStatusContext result = mock(PatchStatusContext.class);
        when(brokerFacade.patchConfigurationDataWithinTransaction(notNull(PatchContext.class)))
            .thenReturn(result);

        List<PatchStatusEntity> patchSTatus = new ArrayList<>();

        PatchStatusEntity entity = new PatchStatusEntity("edit1", true, null);

        patchSTatus.add(entity);

        when(result.getEditCollection())
                .thenReturn(patchSTatus);
        when(result.getGlobalErrors()).thenReturn(new ArrayList<>());
        when(result.getPatchId()).thenReturn("1");

        final String uriPath = "ietf-interfaces:interfaces/yang-ext:mount/test-module:cont/cont1";
        final String payload = loadData("/full-versions/testCont1DataPatch.json");

        final Optional<String> patchResult = this.service.patch(uriPath, payload);

        assertTrue(patchResult.get().contains("\"ok\":[null]"));
    }

    @Test(expected = OperationFailedException.class)
    @SuppressWarnings("checkstyle:IllegalThrows")
    public void testPatchFailure() throws Throwable {
        final PatchStatusContext result = mock(PatchStatusContext.class);
        when(brokerFacade.patchConfigurationDataWithinTransaction(notNull(PatchContext.class)))
            .thenThrow(new TransactionCommitFailedException("Transaction failed"));

        final String uriPath = "ietf-interfaces:interfaces/interface/eth0";
        final String payload = loadData("/parts/ietf-interfaces_interfaces_patch.json");

        final Optional<String> patchResult = this.service.patch(uriPath, payload);

        assertTrue("Patch output is not null", patchResult.isPresent());
        String patch = patchResult.get();
        assertTrue(patch.contains("TransactionCommitFailedException"));
    }

    @Test
    public void testDelete() throws Exception {
        doReturn(CommitInfo.emptyFluentFuture()).when(brokerFacade)
                .commitConfigurationDataDelete(notNull(YangInstanceIdentifier.class));

        final String uriPath = "ietf-interfaces:interfaces/interface/eth0";

        this.service.delete(uriPath);

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        verify(brokerFacade).commitConfigurationDataDelete(capturedPath.capture());

        verifyPath(capturedPath.getValue(), INTERFACES_QNAME, INTERFACE_QNAME,
                new Object[]{INTERFACE_QNAME, NAME_QNAME, "eth0"});
    }

    @Test(expected = OperationFailedException.class)
    public void testDeleteFailure() throws Exception {
        final String invalidUriPath = "ietf-interfaces:interfaces/invalid";

        this.service.delete(invalidUriPath);
    }

    @Test
    public void testGetConfig() throws Exception {
        testGet(LogicalDatastoreType.CONFIGURATION);
    }

    @Test
    public void testGetOperational() throws Exception {
        testGet(LogicalDatastoreType.OPERATIONAL);
    }

    @Test
    public void testGetWithNoData() throws OperationFailedException {
        doReturn(null).when(brokerFacade).readConfigurationData(notNull(YangInstanceIdentifier.class),
                Mockito.anyString());
        final String uriPath = "ietf-interfaces:interfaces";
        this.service.get(uriPath, LogicalDatastoreType.CONFIGURATION);
    }

    @Test(expected = OperationFailedException.class)
    public void testGetFailure() throws Exception {
        final String invalidUriPath = "/ietf-interfaces:interfaces/invalid";
        this.service.get(invalidUriPath, LogicalDatastoreType.CONFIGURATION);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testInvokeRpcWithInput() throws Exception {
        final SchemaPath path = SchemaPath.create(true, MAKE_TOAST_QNAME);

        final DOMRpcResult expResult = new DefaultDOMRpcResult((NormalizedNode<?, ?>)null);
        doReturn(immediateFluentFuture(expResult)).when(brokerFacade).invokeRpc(eq(path), any(NormalizedNode.class));

        final String uriPath = "toaster:make-toast";
        final String input = loadData("/full-versions/make-toast-rpc-input.json");

        final Optional<String> output = this.service.invokeRpc(uriPath, Optional.of(input));

        assertEquals("Output present", false, output.isPresent());

        final ArgumentCaptor<NormalizedNode> capturedNode = ArgumentCaptor.forClass(NormalizedNode.class);
        verify(brokerFacade).invokeRpc(eq(path), capturedNode.capture());

        assertTrue("Expected ContainerNode. Actual " + capturedNode.getValue().getClass(),
                capturedNode.getValue() instanceof ContainerNode);
        final ContainerNode actualNode = (ContainerNode) capturedNode.getValue();
        verifyLeafNode(actualNode, TOASTER_DONENESS_QNAME, 10L);
        verifyLeafNode(actualNode, TOASTER_TYPE_QNAME, WHEAT_BREAD_QNAME);
    }

    @Test
    public void testInvokeRpcWithNoInput() throws Exception {
        final SchemaPath path = SchemaPath.create(true, CANCEL_TOAST_QNAME);

        final DOMRpcResult expResult = new DefaultDOMRpcResult((NormalizedNode<?, ?>)null);
        doReturn(immediateFluentFuture(expResult)).when(brokerFacade).invokeRpc(any(SchemaPath.class), isNull());

        final String uriPath = "toaster:cancel-toast";

        final Optional<String> output = this.service.invokeRpc(uriPath, Optional.absent());

        assertEquals("Output present", false, output.isPresent());

        verify(brokerFacade).invokeRpc(eq(path), isNull());
    }

    @Test
    public void testInvokeRpcWithOutput() throws Exception {
        final SchemaPath path = SchemaPath.create(true, TEST_OUTPUT_QNAME);

        final NormalizedNode<?, ?> outputNode = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(TEST_OUTPUT_QNAME))
                .withChild(ImmutableNodes.leafNode(TEXT_OUT_QNAME, "foo")).build();
        final DOMRpcResult expResult = new DefaultDOMRpcResult(outputNode);
        doReturn(immediateFluentFuture(expResult)).when(brokerFacade).invokeRpc(any(SchemaPath.class), isNull());

        final String uriPath = "toaster:testOutput";

        final Optional<String> output = this.service.invokeRpc(uriPath, Optional.absent());

        assertEquals("Output present", true, output.isPresent());
        assertNotNull("Returned null response", output.get());
        assertThat("Missing \"textOut\"", output.get(), containsString("\"textOut\":\"foo\""));

        verify(brokerFacade).invokeRpc(eq(path), isNull());
    }

    @Test(expected = OperationFailedException.class)
    public void testInvokeRpcFailure() throws Exception {
        final DOMRpcException exception = new DOMRpcImplementationNotAvailableException("testExeption");
        doReturn(immediateFailedFluentFuture(exception)).when(brokerFacade).invokeRpc(any(SchemaPath.class),
                any(NormalizedNode.class));

        final String uriPath = "toaster:cancel-toast";

        this.service.invokeRpc(uriPath, Optional.absent());
    }

    void testGet(final LogicalDatastoreType datastoreType) throws OperationFailedException {
        final MapEntryNode entryNode = ImmutableNodes.mapEntryBuilder(INTERFACE_QNAME, NAME_QNAME, "eth0")
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "eth0"))
                .withChild(ImmutableNodes.leafNode(TYPE_QNAME, "ethernetCsmacd"))
                .withChild(ImmutableNodes.leafNode(ENABLED_QNAME, Boolean.TRUE))
                .withChild(ImmutableNodes.leafNode(DESC_QNAME, "eth interface"))
                .build();

        if (datastoreType == LogicalDatastoreType.CONFIGURATION) {
            doReturn(entryNode).when(brokerFacade).readConfigurationData(notNull(YangInstanceIdentifier.class),
                    isNull());
        } else {
            doReturn(entryNode).when(brokerFacade).readOperationalData(notNull(YangInstanceIdentifier.class));
        }

        final String uriPath = "/ietf-interfaces:interfaces/interface/eth0";

        final Optional<String> optionalResp = this.service.get(uriPath, datastoreType);
        assertEquals("Response present", true, optionalResp.isPresent());
        final String jsonResp = optionalResp.get();

        assertNotNull("Returned null response", jsonResp);
        assertThat("Missing \"name\"", jsonResp, containsString("\"name\":\"eth0\""));
        assertThat("Missing \"type\"", jsonResp, containsString("\"type\":\"ethernetCsmacd\""));
        assertThat("Missing \"enabled\"", jsonResp, containsString("\"enabled\":true"));
        assertThat("Missing \"description\"", jsonResp, containsString("\"description\":\"eth interface\""));

        final ArgumentCaptor<YangInstanceIdentifier> capturedPath =
                ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        if (datastoreType == LogicalDatastoreType.CONFIGURATION) {
            verify(brokerFacade).readConfigurationData(capturedPath.capture(), isNull());
        } else {
            verify(brokerFacade).readOperationalData(capturedPath.capture());
        }

        verifyPath(capturedPath.getValue(), INTERFACES_QNAME, INTERFACE_QNAME,
                new Object[]{INTERFACE_QNAME, NAME_QNAME, "eth0"});
    }

    void verifyLeafNode(final DataContainerNode<?> parent, final QName leafType, final Object leafValue) {
        final java.util.Optional<DataContainerChild<?, ?>> leafChild = parent.getChild(new NodeIdentifier(leafType));
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
                final Map<QName, Object> keyValues = ((NodeIdentifierWithPredicates)actual).getKeyValues();
                assertEquals(String.format("Path arg %d keyValues size", index + 1), 1, keyValues.size());
                final QName expKey = (QName) listEntry[1];
                assertEquals(String.format("Path arg %d keyValue for %s", index + 1, expKey), listEntry[2],
                        keyValues.get(expKey));
            } else {
                expNodeType = (QName) expArgs[index];
            }

            assertEquals(String.format("Path arg %d node type", index + 1), expNodeType, actual.getNodeType());
            index++;
        }

    }
}

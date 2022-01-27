/*
 * Copyright (c) 2014, 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateBooleanFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.PutResult;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.common.ErrorTags;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.CreateDataChangeEventSubscriptionInput1.Scope;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Unit tests for BrokerFacade.
 *
 * @author Thomas Pantelis
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class BrokerFacadeTest {

    @Mock
    private DOMDataBroker domDataBroker;
    @Mock
    private DOMNotificationService domNotification;
    @Mock
    private DOMRpcService mockRpcService;
    @Mock
    private DOMMountPoint mockMountInstance;
    @Mock
    private DOMDataTreeReadTransaction readTransaction;
    @Mock
    private DOMDataTreeWriteTransaction writeTransaction;
    @Mock
    private DOMDataTreeReadWriteTransaction rwTransaction;

    private BrokerFacade brokerFacade;
    private final NormalizedNode dummyNode = createDummyNode("test:module", "2014-01-09", "interfaces");
    private final FluentFuture<Optional<NormalizedNode>> dummyNodeInFuture = wrapDummyNode(dummyNode);
    private final QName qname = TestUtils.buildQName("interfaces","test:module", "2014-01-09");
    private final YangInstanceIdentifier instanceID = YangInstanceIdentifier.builder().node(qname).build();
    private ControllerContext controllerContext;

    @Before
    public void setUp() throws Exception {
        controllerContext = TestRestconfUtils.newControllerContext(
                TestUtils.loadSchemaContext("/full-versions/test-module", "/modules"));

        brokerFacade = BrokerFacade.newInstance(mockRpcService, domDataBroker, domNotification, controllerContext);

        when(domDataBroker.newReadOnlyTransaction()).thenReturn(readTransaction);
        when(domDataBroker.newReadWriteTransaction()).thenReturn(rwTransaction);
        when(domDataBroker.getExtensions()).thenReturn(ImmutableClassToInstanceMap.of(
            DOMDataTreeChangeService.class, Mockito.mock(DOMDataTreeChangeService.class)));
    }

    private static FluentFuture<Optional<NormalizedNode>> wrapDummyNode(final NormalizedNode dummyNode) {
        return immediateFluentFuture(Optional.of(dummyNode));
    }

    private static FluentFuture<Boolean> wrapExistence(final boolean exists) {
        return immediateBooleanFluentFuture(exists);
    }

    /**
     * Value of this node shouldn't be important for testing purposes.
     */
    private static NormalizedNode createDummyNode(final String namespace, final String date, final String localName) {
        return Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(namespace, date, localName)))
                .build();
    }

    @Test
    public void testReadConfigurationData() {
        when(readTransaction.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class))).thenReturn(
                dummyNodeInFuture);

        final NormalizedNode actualNode = brokerFacade.readConfigurationData(instanceID);

        assertSame("readConfigurationData", dummyNode, actualNode);
    }

    @Test
    public void testReadOperationalData() {
        when(readTransaction.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class))).thenReturn(
                dummyNodeInFuture);

        final NormalizedNode actualNode = brokerFacade.readOperationalData(instanceID);

        assertSame("readOperationalData", dummyNode, actualNode);
    }

    @Test
    public void test503() throws Exception {
        final RpcError error = RpcResultBuilder.newError(ErrorType.TRANSPORT, ErrorTag.RESOURCE_DENIED,
                "Master is down. Please try again.");
        doReturn(immediateFailedFluentFuture(new ReadFailedException("Read from transaction failed", error)))
                .when(readTransaction).read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class));

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> brokerFacade.readConfigurationData(instanceID, "explicit"));
        final List<RestconfError> errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals("getErrorTag", ErrorTags.RESOURCE_DENIED_TRANSPORT, errors.get(0).getErrorTag());
        assertEquals("getErrorType", ErrorType.TRANSPORT,errors.get(0).getErrorType());
        assertEquals("getErrorMessage", "Master is down. Please try again.", errors.get(0).getErrorMessage());
    }

    @Test
    public void testInvokeRpc() throws Exception {
        final DOMRpcResult expResult = mock(DOMRpcResult.class);
        doReturn(immediateFluentFuture(expResult)).when(mockRpcService).invokeRpc(qname, dummyNode);

        final ListenableFuture<? extends DOMRpcResult> actualFuture = brokerFacade.invokeRpc(qname,
            dummyNode);
        assertNotNull("Future is null", actualFuture);
        final DOMRpcResult actualResult = actualFuture.get();
        assertSame("invokeRpc", expResult, actualResult);
    }

    @Test
    public void testCommitConfigurationDataPut() throws Exception {
        doReturn(CommitInfo.emptyFluentFuture()).when(rwTransaction).commit();

        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(rwTransaction)
        .read(LogicalDatastoreType.CONFIGURATION, instanceID);

        final PutResult result = brokerFacade.commitConfigurationDataPut(mock(EffectiveModelContext.class),
                instanceID, dummyNode, null, null);

        assertSame("commitConfigurationDataPut", CommitInfo.emptyFluentFuture(), result.getFutureOfPutData());

        final InOrder inOrder = inOrder(domDataBroker, rwTransaction);
        inOrder.verify(domDataBroker).newReadWriteTransaction();
        inOrder.verify(rwTransaction).put(LogicalDatastoreType.CONFIGURATION, instanceID, dummyNode);
        inOrder.verify(rwTransaction).commit();
    }

    @Test
    public void testCommitConfigurationDataPost() {
        when(rwTransaction.exists(LogicalDatastoreType.CONFIGURATION, instanceID))
                .thenReturn(wrapExistence(false));

        doReturn(CommitInfo.emptyFluentFuture()).when(rwTransaction).commit();

        final FluentFuture<? extends CommitInfo> actualFuture = brokerFacade
                .commitConfigurationDataPost(mock(EffectiveModelContext.class), instanceID, dummyNode, null,
                        null);

        assertSame("commitConfigurationDataPost", CommitInfo.emptyFluentFuture(), actualFuture);

        final InOrder inOrder = inOrder(domDataBroker, rwTransaction);
        inOrder.verify(domDataBroker).newReadWriteTransaction();
        inOrder.verify(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, instanceID);
        inOrder.verify(rwTransaction).put(LogicalDatastoreType.CONFIGURATION, instanceID, dummyNode);
        inOrder.verify(rwTransaction).commit();
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testCommitConfigurationDataPostAlreadyExists() {
        when(rwTransaction.exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class)))
                .thenReturn(immediateTrueFluentFuture());
        try {
            // Schema context is only necessary for ensuring parent structure
            brokerFacade.commitConfigurationDataPost((EffectiveModelContext) null, instanceID, dummyNode,
                    null, null);
        } catch (final RestconfDocumentedException e) {
            assertEquals("getErrorTag", ErrorTag.DATA_EXISTS, e.getErrors().get(0).getErrorTag());
            throw e;
        }
    }

    /**
     * Positive test of delete operation when data to delete exits. Returned value and order of steps are validated.
     */
    @Test
    public void testCommitConfigurationDataDelete() throws Exception {
        // assume that data to delete exists
        prepareDataForDelete(true);

        // expected result
        doReturn(CommitInfo.emptyFluentFuture()).when(rwTransaction).commit();

        // test
        final FluentFuture<? extends CommitInfo> actualFuture = brokerFacade
                .commitConfigurationDataDelete(instanceID);

        // verify result and interactions
        assertSame("commitConfigurationDataDelete", CommitInfo.emptyFluentFuture(), actualFuture);

        // check exists, delete, submit
        final InOrder inOrder = inOrder(domDataBroker, rwTransaction);
        inOrder.verify(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, instanceID);
        inOrder.verify(rwTransaction).delete(LogicalDatastoreType.CONFIGURATION, instanceID);
        inOrder.verify(rwTransaction).commit();
    }

    /**
     * Negative test of delete operation when data to delete does not exist. Error DATA_MISSING should be returned.
     */
    @Test
    public void testCommitConfigurationDataDeleteNoData() throws Exception {
        // assume that data to delete does not exist
        prepareDataForDelete(false);

        // try to delete and expect DATA_MISSING error
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> brokerFacade.commitConfigurationDataDelete(instanceID));
        final List<RestconfError> errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorType.PROTOCOL, errors.get(0).getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, errors.get(0).getErrorTag());
    }

    /**
     * Prepare conditions to test delete operation. Data to delete exists or does not exist according to value of
     * {@code assumeDataExists} parameter.
     * @param assumeDataExists boolean to assume if data exists
     */
    private void prepareDataForDelete(final boolean assumeDataExists) {
        when(rwTransaction.exists(LogicalDatastoreType.CONFIGURATION, instanceID))
                .thenReturn(immediateBooleanFluentFuture(assumeDataExists));
    }

    @Test
    public void testRegisterToListenDataChanges() {
        final ListenerAdapter listener = Notificator.createListener(instanceID, "stream",
                NotificationOutputType.XML, controllerContext);

        @SuppressWarnings("unchecked")
        final ListenerRegistration<ListenerAdapter> mockRegistration = mock(ListenerRegistration.class);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier loc = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, instanceID);
        when(changeService.registerDataTreeChangeListener(eq(loc), eq(listener))).thenReturn(mockRegistration);

        brokerFacade.registerToListenDataChanges(LogicalDatastoreType.CONFIGURATION, Scope.BASE, listener);

        verify(changeService).registerDataTreeChangeListener(loc, listener);

        assertEquals("isListening", true, listener.isListening());

        brokerFacade.registerToListenDataChanges(LogicalDatastoreType.CONFIGURATION, Scope.BASE, listener);
        verifyNoMoreInteractions(changeService);
    }

    /**
     * Create, register, close and remove notification listener.
     */
    @Test
    public void testRegisterToListenNotificationChanges() throws Exception {
        // create test notification listener
        final String identifier = "create-notification-stream/toaster:toastDone";
        Notificator.createNotificationListener(
            List.of(Absolute.of(QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toastDone"))),
            identifier, "XML", controllerContext);
        final NotificationListenerAdapter listener = Notificator.getNotificationListenerFor(identifier).get(0);

        // mock registration
        final ListenerRegistration<NotificationListenerAdapter> registration = mock(ListenerRegistration.class);
        when(domNotification.registerNotificationListener(listener, listener.getSchemaPath()))
                .thenReturn(registration);

        // test to register listener for the first time
        brokerFacade.registerToListenNotification(listener);
        assertEquals("Registration was not successful", true, listener.isListening());

        // try to register for the second time
        brokerFacade.registerToListenNotification(listener);
        assertEquals("Registration was not successful", true, listener.isListening());

        // registrations should be invoked only once
        verify(domNotification, times(1)).registerNotificationListener(listener, listener.getSchemaPath());

        final DOMTransactionChain transactionChain = mock(DOMTransactionChain.class);
        final DOMDataTreeWriteTransaction wTx = mock(DOMDataTreeWriteTransaction.class);
        // close and remove test notification listener
        listener.close();
        Notificator.removeListenerIfNoSubscriberExists(listener);
    }

    /**
     * Test Patch method on the server with no data.
     */
    @Test
    public void testPatchConfigurationDataWithinTransactionServer() throws Exception {
        final PatchContext patchContext = mock(PatchContext.class);

        when(patchContext.getData()).thenReturn(List.of());
        // no mount point
        doReturn(InstanceIdentifierContext.ofPath(SchemaInferenceStack.of(mock(EffectiveModelContext.class)),
            mock(DataSchemaNode.class), YangInstanceIdentifier.empty(), null))
                .when(patchContext).getInstanceIdentifierContext();

        doReturn(CommitInfo.emptyFluentFuture()).when(rwTransaction).commit();

        final PatchStatusContext status = brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

        // assert success
        assertTrue("Patch operation should be successful on server", status.isOk());
    }

    /**
     * Test Patch method on mounted device with no data.
     */
    @Test
    public void testPatchConfigurationDataWithinTransactionMount() throws Exception {
        final PatchContext patchContext = mock(PatchContext.class);
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        final DOMDataBroker mountDataBroker = mock(DOMDataBroker.class);
        final DOMDataTreeReadWriteTransaction transaction = mock(DOMDataTreeReadWriteTransaction.class);

        when(patchContext.getData()).thenReturn(List.of());
        // return mount point with broker
        doReturn(InstanceIdentifierContext.ofPath(SchemaInferenceStack.of(mock(EffectiveModelContext.class)),
            mock(DataSchemaNode.class), YangInstanceIdentifier.empty(), mountPoint))
                .when(patchContext).getInstanceIdentifierContext();

        when(mountPoint.getService(DOMDataBroker.class)).thenReturn(Optional.of(mountDataBroker));
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.empty());
        when(mountDataBroker.newReadWriteTransaction()).thenReturn(transaction);
        doReturn(CommitInfo.emptyFluentFuture()).when(transaction).commit();

        final PatchStatusContext status = brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

        // assert success
        assertTrue("Patch operation should be successful on mounted device", status.isOk());
    }

    /**
     * Negative test for Patch operation when mounted device does not support {@link DOMDataBroker service.}
     * Patch operation should fail with global error.
     */
    @Test
    public void testPatchConfigurationDataWithinTransactionMountFail() throws Exception {
        final PatchContext patchContext = mock(PatchContext.class);
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);

        doReturn(InstanceIdentifierContext.ofPath(SchemaInferenceStack.of(mock(EffectiveModelContext.class)),
            mock(DataSchemaNode.class), YangInstanceIdentifier.empty(), mountPoint))
                .when(patchContext).getInstanceIdentifierContext();

        // missing broker on mounted device
        when(mountPoint.getService(DOMDataBroker.class)).thenReturn(Optional.empty());
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.empty());

        final PatchStatusContext status = brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

        // assert not successful operation with error
        assertNotNull(status.getGlobalErrors());
        assertEquals(1, status.getGlobalErrors().size());
        assertEquals(ErrorType.APPLICATION, status.getGlobalErrors().get(0).getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, status.getGlobalErrors().get(0).getErrorTag());

        assertFalse("Patch operation should fail on mounted device without Broker", status.isOk());
    }
}

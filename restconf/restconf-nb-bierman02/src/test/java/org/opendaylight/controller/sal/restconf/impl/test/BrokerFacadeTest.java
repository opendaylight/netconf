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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
//import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
//import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.PutResult;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
//import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
//import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708
// .NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Unit tests for BrokerFacade.
 *
 * @author Thomas Pantelis
 */
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
    private DOMDataReadOnlyTransaction readTransaction;
    @Mock
    private DOMDataWriteTransaction writeTransaction;
    @Mock
    private DOMDataReadWriteTransaction rwTransaction;

    private final BrokerFacade brokerFacade = BrokerFacade.getInstance();
    private final NormalizedNode<?, ?> dummyNode = createDummyNode("test:module", "2014-01-09", "interfaces");
    private final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> dummyNodeInFuture =
            wrapDummyNode(this.dummyNode);
    private final QName qname = TestUtils.buildQName("interfaces","test:module", "2014-01-09");
    private final SchemaPath type = SchemaPath.create(true, this.qname);
    private final YangInstanceIdentifier instanceID = YangInstanceIdentifier.builder().node(this.qname).build();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.brokerFacade.setDomDataBroker(this.domDataBroker);
        this.brokerFacade.setDomNotificationService(this.domNotification);
        this.brokerFacade.setRpcService(this.mockRpcService);
        when(this.domDataBroker.newReadOnlyTransaction()).thenReturn(this.readTransaction);
        when(this.domDataBroker.newWriteOnlyTransaction()).thenReturn(this.writeTransaction);
        when(this.domDataBroker.newReadWriteTransaction()).thenReturn(this.rwTransaction);

        ControllerContext.getInstance()
                .setSchemas(TestUtils.loadSchemaContext("/full-versions/test-module", "/modules"));
    }

    private static CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> wrapDummyNode(
            final NormalizedNode<?, ?> dummyNode) {
        return Futures.immediateCheckedFuture(Optional.<NormalizedNode<?, ?>>of(dummyNode));
    }

    private static CheckedFuture<Boolean, ReadFailedException> wrapExistence(final Boolean exists) {
        return Futures.immediateCheckedFuture(exists);
    }

    /**
     * Value of this node shouldn't be important for testing purposes.
     */
    private static NormalizedNode<?, ?> createDummyNode(final String namespace, final String date,
            final String localName) {
        return Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(namespace, date, localName))).build();
    }

    @Test
    public void testReadConfigurationData() {
        when(this.readTransaction.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class))).thenReturn(
                this.dummyNodeInFuture);

        final NormalizedNode<?, ?> actualNode = this.brokerFacade.readConfigurationData(this.instanceID);

        assertSame("readConfigurationData", this.dummyNode, actualNode);
    }

    @Test
    public void testReadOperationalData() {
        when(this.readTransaction.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class))).thenReturn(
                this.dummyNodeInFuture);

        final NormalizedNode<?, ?> actualNode = this.brokerFacade.readOperationalData(this.instanceID);

        assertSame("readOperationalData", this.dummyNode, actualNode);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testReadOperationalDataWithNoDataBroker() {
        this.brokerFacade.setDomDataBroker(null);

        this.brokerFacade.readOperationalData(this.instanceID);
    }

    @Test
    public void test503() throws Exception {
        final RpcError error = RpcResultBuilder.newError(
                RpcError.ErrorType.TRANSPORT,
                ErrorTag.RESOURCE_DENIED.getTagValue(),
                "Master is down. Please try again.");
        final ReadFailedException exception503 = new ReadFailedException("Read from transaction failed", error);
        doReturn(Futures.immediateFailedCheckedFuture(exception503))
                .when(readTransaction).read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class));
        try {
            brokerFacade.readConfigurationData(this.instanceID, "explicit");
            fail("This test should fail.");
        } catch (final RestconfDocumentedException e) {
            assertEquals("getErrorTag", ErrorTag.RESOURCE_DENIED_TRANSPORT, e.getErrors().get(0).getErrorTag());
            assertEquals("getErrorType", ErrorType.TRANSPORT, e.getErrors().get(0).getErrorType());
            assertEquals("getErrorMessage", "Master is down. Please try again.",
                    e.getErrors().get(0).getErrorMessage());
        }
    }

    @Test
    public void testInvokeRpc() throws Exception {
        final DOMRpcResult expResult = mock(DOMRpcResult.class);
        final CheckedFuture<DOMRpcResult, DOMRpcException> future = Futures.immediateCheckedFuture(expResult);
        when(this.mockRpcService.invokeRpc(this.type, this.dummyNode)).thenReturn(future);

        final CheckedFuture<DOMRpcResult, DOMRpcException> actualFuture = this.brokerFacade
                .invokeRpc(this.type, this.dummyNode);
        assertNotNull("Future is null", actualFuture);
        final DOMRpcResult actualResult = actualFuture.get();
        assertSame("invokeRpc", expResult, actualResult);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testInvokeRpcWithNoConsumerSession() {
        brokerFacade.setDomDataBroker(null);
        this.brokerFacade.invokeRpc(this.type, this.dummyNode);
    }

    @Test
    public void testCommitConfigurationDataPut() throws Exception {
        @SuppressWarnings("unchecked")
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = mock(CheckedFuture.class);
        when(this.rwTransaction.submit()).thenReturn(expFuture);

        final Optional<NormalizedNode<?, ?>> optionalMock = mock(Optional.class);
        when(optionalMock.get()).thenReturn(null);

        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readFuture = Futures
                .immediateCheckedFuture(optionalMock);
        when(this.rwTransaction.read(LogicalDatastoreType.CONFIGURATION, this.instanceID)).thenReturn(readFuture);

        final PutResult result = this.brokerFacade.commitConfigurationDataPut(mock(SchemaContext.class),
                this.instanceID, this.dummyNode, null, null);

        final Future<Void> actualFuture = result.getFutureOfPutData();

        assertSame("commitConfigurationDataPut", expFuture, actualFuture);

        final InOrder inOrder = inOrder(this.domDataBroker, this.rwTransaction);
        inOrder.verify(this.domDataBroker).newReadWriteTransaction();
        inOrder.verify(this.rwTransaction).put(LogicalDatastoreType.CONFIGURATION, this.instanceID, this.dummyNode);
        inOrder.verify(this.rwTransaction).submit();
    }

    @Test
    public void testCommitConfigurationDataPost() {
        @SuppressWarnings("unchecked")
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = mock(CheckedFuture.class);

        when(this.rwTransaction.exists(LogicalDatastoreType.CONFIGURATION, this.instanceID))
                .thenReturn(wrapExistence(false));

        when(this.rwTransaction.submit()).thenReturn(expFuture);

        final CheckedFuture<Void, TransactionCommitFailedException> actualFuture = this.brokerFacade
                .commitConfigurationDataPost(mock(SchemaContext.class), this.instanceID, this.dummyNode, null, null);

        assertSame("commitConfigurationDataPost", expFuture, actualFuture);

        final InOrder inOrder = inOrder(this.domDataBroker, this.rwTransaction);
        inOrder.verify(this.domDataBroker).newReadWriteTransaction();
        inOrder.verify(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, this.instanceID);
        inOrder.verify(this.rwTransaction).put(LogicalDatastoreType.CONFIGURATION, this.instanceID, this.dummyNode);
        inOrder.verify(this.rwTransaction).submit();
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testCommitConfigurationDataPostAlreadyExists() {
        final CheckedFuture<Boolean, ReadFailedException> successFuture = Futures.immediateCheckedFuture(Boolean.TRUE);
        when(this.rwTransaction.exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class)))
                .thenReturn(successFuture);
        try {
            // Schema context is only necessary for ensuring parent structure
            this.brokerFacade.commitConfigurationDataPost((SchemaContext) null, this.instanceID, this.dummyNode, null,
                    null);
        } catch (final RestconfDocumentedException e) {
            assertEquals("getErrorTag", RestconfError.ErrorTag.DATA_EXISTS, e.getErrors().get(0).getErrorTag());
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
        @SuppressWarnings("unchecked")
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = mock(CheckedFuture.class);
        when(this.rwTransaction.submit()).thenReturn(expFuture);

        // test
        final CheckedFuture<Void, TransactionCommitFailedException> actualFuture = this.brokerFacade
                .commitConfigurationDataDelete(this.instanceID);

        // verify result and interactions
        assertSame("commitConfigurationDataDelete", expFuture, actualFuture);

        // check exists, delete, submit
        final InOrder inOrder = inOrder(this.domDataBroker, this.rwTransaction);
        inOrder.verify(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, this.instanceID);
        inOrder.verify(this.rwTransaction).delete(LogicalDatastoreType.CONFIGURATION, this.instanceID);
        inOrder.verify(this.rwTransaction).submit();
    }

    /**
     * Negative test of delete operation when data to delete does not exist. Error 404 should be returned.
     */
    @Test
    public void testCommitConfigurationDataDeleteNoData() throws Exception {
        // assume that data to delete does not exist
        prepareDataForDelete(false);

        // try to delete and expect 404 error
        try {
            this.brokerFacade.commitConfigurationDataDelete(this.instanceID);
            fail("Delete operation should fail due to missing data");
        } catch (final RestconfDocumentedException e) {
            assertEquals(ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals(404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Prepare conditions to test delete operation. Data to delete exists or does not exist according to value of
     * {@code assumeDataExists} parameter.
     * @param assumeDataExists boolean to assume if data exists
     */
    private void prepareDataForDelete(final boolean assumeDataExists) {
        when(this.rwTransaction.exists(LogicalDatastoreType.CONFIGURATION, this.instanceID))
                .thenReturn(Futures.immediateCheckedFuture(new Boolean(assumeDataExists)));
    }

    //TBD: JOSH: FIGURE OUT WHAT THIS TESTS AND FIX IT
//    @Test
//    public void testRegisterToListenDataChanges() {
//        final ListenerAdapter listener = Notificator.createListener(this.instanceID, "stream",
//                NotificationOutputType.XML);
//
//        @SuppressWarnings("unchecked")
//        final ListenerRegistration<DOMDataChangeListener> mockRegistration = mock(ListenerRegistration.class);
//
//        when(this.domDataBroker.registerDataChangeListener(any(LogicalDatastoreType.class), eq(this.instanceID),
//                eq(listener), eq(DataChangeScope.BASE))).thenReturn(mockRegistration);
//
//        this.brokerFacade.registerToListenDataChanges(
//                LogicalDatastoreType.CONFIGURATION, DataChangeScope.BASE, listener);
//
//        verify(this.domDataBroker).registerDataChangeListener(
//                LogicalDatastoreType.CONFIGURATION, this.instanceID, listener, DataChangeScope.BASE);
//
//        assertEquals("isListening", true, listener.isListening());
//
//        this.brokerFacade.registerToListenDataChanges(
//                LogicalDatastoreType.CONFIGURATION, DataChangeScope.BASE, listener);
//        verifyNoMoreInteractions(this.domDataBroker);
//    }

    /**
     * Create, register, close and remove notification listener.
     */
    @Test
    public void testRegisterToListenNotificationChanges() throws Exception {
        // create test notification listener
        final String identifier = "create-notification-stream/toaster:toastDone";
        final SchemaPath path = SchemaPath.create(true,
                QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toastDone"));
        Notificator.createNotificationListener(Lists.newArrayList(path), identifier, "XML");
        final NotificationListenerAdapter listener = Notificator.getNotificationListenerFor(identifier).get(0);

        // mock registration
        final ListenerRegistration<NotificationListenerAdapter> registration = mock(ListenerRegistration.class);
        when(this.domNotification.registerNotificationListener(listener, listener.getSchemaPath()))
                .thenReturn(registration);

        // test to register listener for the first time
        this.brokerFacade.registerToListenNotification(listener);
        assertEquals("Registration was not successful", true, listener.isListening());

        // try to register for the second time
        this.brokerFacade.registerToListenNotification(listener);
        assertEquals("Registration was not successful", true, listener.isListening());

        // registrations should be invoked only once
        verify(this.domNotification, times(1)).registerNotificationListener(listener, listener.getSchemaPath());

        final DOMTransactionChain transactionChain = mock(DOMTransactionChain.class);
        final DOMDataWriteTransaction wTx = mock(DOMDataWriteTransaction.class);
        final CheckedFuture<Void, TransactionCommitFailedException> checked = Futures.immediateCheckedFuture(null);
        when(wTx.submit()).thenReturn(checked);
        when(transactionChain.newWriteOnlyTransaction()).thenReturn(wTx);
        // close and remove test notification listener
        listener.close();
        Notificator.removeListenerIfNoSubscriberExists(listener);
    }

    /**
     * Test Patch method on the server with no data.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testPatchConfigurationDataWithinTransactionServer() throws Exception {
        final PatchContext patchContext = mock(PatchContext.class);
        final InstanceIdentifierContext identifierContext = mock(InstanceIdentifierContext.class);
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = Futures.immediateCheckedFuture(null);

        when(patchContext.getData()).thenReturn(Lists.newArrayList());
        when(patchContext.getInstanceIdentifierContext()).thenReturn(identifierContext);

        // no mount point
        when(identifierContext.getMountPoint()).thenReturn(null);

        when(this.rwTransaction.submit()).thenReturn(expFuture);

        final PatchStatusContext status = this.brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

        // assert success
        assertTrue("Patch operation should be successful on server", status.isOk());
    }

    /**
     * Test Patch method on mounted device with no data.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testPatchConfigurationDataWithinTransactionMount() throws Exception {
        final PatchContext patchContext = mock(PatchContext.class);
        final InstanceIdentifierContext identifierContext = mock(InstanceIdentifierContext.class);
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        final DOMDataBroker mountDataBroker = mock(DOMDataBroker.class);
        final DOMDataReadWriteTransaction transaction = mock(DOMDataReadWriteTransaction.class);
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = Futures.immediateCheckedFuture(null);

        when(patchContext.getData()).thenReturn(Lists.newArrayList());
        when(patchContext.getInstanceIdentifierContext()).thenReturn(identifierContext);

        // return mount point with broker
        when(identifierContext.getMountPoint()).thenReturn(mountPoint);
        when(mountPoint.getService(DOMDataBroker.class)).thenReturn(Optional.of(mountDataBroker));
        when(mountDataBroker.newReadWriteTransaction()).thenReturn(transaction);
        when(transaction.submit()).thenReturn(expFuture);

        final PatchStatusContext status = this.brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

        // assert success
        assertTrue("Patch operation should be successful on mounted device", status.isOk());
    }

    /**
     * Negative test for Patch operation when mounted device does not support {@link DOMDataBroker service.}
     * Patch operation should fail with global error.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testPatchConfigurationDataWithinTransactionMountFail() throws Exception {
        final PatchContext patchContext = mock(PatchContext.class);
        final InstanceIdentifierContext identifierContext = mock(InstanceIdentifierContext.class);
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        final DOMDataBroker mountDataBroker = mock(DOMDataBroker.class);
        final DOMDataReadWriteTransaction transaction = mock(DOMDataReadWriteTransaction.class);
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = Futures.immediateCheckedFuture(null);

        when(patchContext.getData()).thenReturn(Lists.newArrayList());
        when(patchContext.getInstanceIdentifierContext()).thenReturn(identifierContext);
        when(identifierContext.getMountPoint()).thenReturn(mountPoint);

        // missing broker on mounted device
        when(mountPoint.getService(DOMDataBroker.class)).thenReturn(Optional.absent());

        when(mountDataBroker.newReadWriteTransaction()).thenReturn(transaction);
        when(transaction.submit()).thenReturn(expFuture);

        final PatchStatusContext status = this.brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

        // assert not successful operation with error
        assertNotNull(status.getGlobalErrors());
        assertEquals(1, status.getGlobalErrors().size());
        assertEquals(ErrorType.APPLICATION, status.getGlobalErrors().get(0).getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, status.getGlobalErrors().get(0).getErrorTag());

        assertFalse("Patch operation should fail on mounted device without Broker", status.isOk());
    }
}

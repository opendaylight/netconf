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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateBooleanFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.PutResult;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

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
    private DOMDataTreeReadTransaction readTransaction;
    @Mock
    private DOMDataTreeWriteTransaction writeTransaction;
    @Mock
    private DOMDataTreeReadWriteTransaction rwTransaction;
    @Mock
    private FluentFuture<Boolean> existsFluentFuture;

    private BrokerFacade brokerFacade;
    private final NormalizedNode<?, ?> dummyNode = createDummyNode("test:module", "2014-01-09", "interfaces");
    private final FluentFuture<Optional<NormalizedNode<?, ?>>> dummyNodeInFuture = wrapDummyNode(this.dummyNode);
    private final QName qname = TestUtils.buildQName("interfaces","test:module", "2014-01-09");
    private final YangInstanceIdentifier instanceID = YangInstanceIdentifier.builder().node(this.qname).build();
    private ControllerContext controllerContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        controllerContext = TestRestconfUtils.newControllerContext(
                TestUtils.loadSchemaContext("/full-versions/test-module", "/modules"));

        brokerFacade = BrokerFacade.newInstance(mockRpcService, domDataBroker, domNotification, controllerContext);

        when(this.domDataBroker.newReadOnlyTransaction()).thenReturn(this.readTransaction);
        when(this.domDataBroker.newWriteOnlyTransaction()).thenReturn(this.writeTransaction);
        when(this.domDataBroker.newReadWriteTransaction()).thenReturn(this.rwTransaction);
        when(this.domDataBroker.getExtensions()).thenReturn(ImmutableClassToInstanceMap.of(
            DOMDataTreeChangeService.class, Mockito.mock(DOMDataTreeChangeService.class)));
    }

    private static FluentFuture<Optional<NormalizedNode<?, ?>>> wrapDummyNode(final NormalizedNode<?, ?> dummyNode) {
        return immediateFluentFuture(Optional.of(dummyNode));
    }

    private static FluentFuture<Boolean> wrapExistence(final boolean exists) {
        return immediateBooleanFluentFuture(exists);
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

    @Test
    public void test503() throws Exception {
        final RpcError error = RpcResultBuilder.newError(
                RpcError.ErrorType.TRANSPORT,
                ErrorTag.RESOURCE_DENIED.getTagValue(),
                "Master is down. Please try again.");
        doReturn(immediateFailedFluentFuture(new ReadFailedException("Read from transaction failed", error)))
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
        doReturn(immediateFluentFuture(expResult)).when(this.mockRpcService).invokeRpc(this.qname, this.dummyNode);

        final ListenableFuture<? extends DOMRpcResult> actualFuture = this.brokerFacade.invokeRpc(this.qname,
            this.dummyNode);
        assertNotNull("Future is null", actualFuture);
        final DOMRpcResult actualResult = actualFuture.get();
        assertSame("invokeRpc", expResult, actualResult);
    }

    @Test
    public void testCommitConfigurationDataPut() throws Exception {
        doReturn(CommitInfo.emptyFluentFuture()).when(this.rwTransaction).commit();

        doReturn(immediateFluentFuture(Optional.of(mock(NormalizedNode.class)))).when(this.rwTransaction)
        .read(LogicalDatastoreType.CONFIGURATION, this.instanceID);

        final PutResult result = this.brokerFacade.commitConfigurationDataPut(mock(EffectiveModelContext.class),
                this.instanceID, this.dummyNode, null, null);

        assertSame("commitConfigurationDataPut", CommitInfo.emptyFluentFuture(), result.getFutureOfPutData());

        final InOrder inOrder = inOrder(this.domDataBroker, this.rwTransaction);
        inOrder.verify(this.domDataBroker).newReadWriteTransaction();
        inOrder.verify(this.rwTransaction).put(LogicalDatastoreType.CONFIGURATION, this.instanceID, this.dummyNode);
        inOrder.verify(this.rwTransaction).commit();
    }

    @Test
    public void testCommitConfigurationDataPost() {
        when(this.rwTransaction.exists(LogicalDatastoreType.CONFIGURATION, this.instanceID))
                .thenReturn(wrapExistence(false));

        doReturn(CommitInfo.emptyFluentFuture()).when(this.rwTransaction).commit();

        final FluentFuture<? extends CommitInfo> actualFuture = this.brokerFacade
                .commitConfigurationDataPost(mock(EffectiveModelContext.class), this.instanceID, this.dummyNode, null,
                        null);

        assertSame("commitConfigurationDataPost", CommitInfo.emptyFluentFuture(), actualFuture);

        final InOrder inOrder = inOrder(this.domDataBroker, this.rwTransaction);
        inOrder.verify(this.domDataBroker).newReadWriteTransaction();
        inOrder.verify(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, this.instanceID);
        inOrder.verify(this.rwTransaction).put(LogicalDatastoreType.CONFIGURATION, this.instanceID, this.dummyNode);
        inOrder.verify(this.rwTransaction).commit();
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testCommitConfigurationDataPostAlreadyExists() {
        when(this.rwTransaction.exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class)))
                .thenReturn(immediateTrueFluentFuture());
        try {
            // Schema context is only necessary for ensuring parent structure
            this.brokerFacade.commitConfigurationDataPost((EffectiveModelContext) null, this.instanceID, this.dummyNode,
                    null, null);
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
        doReturn(CommitInfo.emptyFluentFuture()).when(this.rwTransaction).commit();

        // test
        final FluentFuture<? extends CommitInfo> actualFuture = this.brokerFacade
                .commitConfigurationDataDelete(this.instanceID);

        // verify result and interactions
        assertSame("commitConfigurationDataDelete", CommitInfo.emptyFluentFuture(), actualFuture);

        // check exists, delete, submit
        final InOrder inOrder = inOrder(this.domDataBroker, this.rwTransaction);
        inOrder.verify(this.rwTransaction).exists(LogicalDatastoreType.CONFIGURATION, this.instanceID);
        inOrder.verify(this.rwTransaction).delete(LogicalDatastoreType.CONFIGURATION, this.instanceID);
        inOrder.verify(this.rwTransaction).commit();
    }

    /**
     * Negative test of delete operation when data to delete does not exist. Error DATA_MISSING should be returned.
     */
    @Test
    public void testCommitConfigurationDataDeleteNoData() throws Exception {
        // assume that data to delete does not exist
        prepareDataForDelete(false);

        // try to delete and expect DATA_MISSING error
        try {
            this.brokerFacade.commitConfigurationDataDelete(this.instanceID);
            fail("Delete operation should fail due to missing data");
        } catch (final RestconfDocumentedException e) {
            assertEquals(ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals(ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
        }
    }

    /**
     * Prepare conditions to test delete operation. Data to delete exists or does not exist according to value of
     * {@code assumeDataExists} parameter.
     * @param assumeDataExists boolean to assume if data exists
     */
    private void prepareDataForDelete(final boolean assumeDataExists) {
        when(this.rwTransaction.exists(LogicalDatastoreType.CONFIGURATION, this.instanceID))
                .thenReturn(immediateBooleanFluentFuture(assumeDataExists));
    }

    @Test
    public void testExitsforLockDenied() throws Exception {
        doThrow(new ExecutionException(XmlNetconfConstants.LOCK_DENIED, null)).when(existsFluentFuture).get();
        this.rwTransaction.exists(LogicalDatastoreType.CONFIGURATION, this.instanceID);
        verify(rwTransaction, never()).cancel();
    }

    @Test
    public void testRegisterToListenDataChanges() {
        final ListenerAdapter listener = Notificator.createListener(this.instanceID, "stream",
                NotificationOutputType.XML, controllerContext);

        @SuppressWarnings("unchecked")
        final ListenerRegistration<ListenerAdapter> mockRegistration = mock(ListenerRegistration.class);

        DOMDataTreeChangeService changeService = this.domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier loc = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, this.instanceID);
        when(changeService.registerDataTreeChangeListener(eq(loc), eq(listener))).thenReturn(mockRegistration);

        this.brokerFacade.registerToListenDataChanges(
                LogicalDatastoreType.CONFIGURATION, DataChangeScope.BASE, listener);

        verify(changeService).registerDataTreeChangeListener(loc, listener);

        assertEquals("isListening", true, listener.isListening());

        this.brokerFacade.registerToListenDataChanges(
                LogicalDatastoreType.CONFIGURATION, DataChangeScope.BASE, listener);
        verifyNoMoreInteractions(changeService);
    }

    /**
     * Create, register, close and remove notification listener.
     */
    @Test
    public void testRegisterToListenNotificationChanges() throws Exception {
        // create test notification listener
        final String identifier = "create-notification-stream/toaster:toastDone";
        final SchemaPath path = SchemaPath.create(true,
                QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toastDone"));
        Notificator.createNotificationListener(Lists.newArrayList(path), identifier, "XML", controllerContext);
        final NotificationListenerAdapter listener = Notificator.getNotificationListenerFor(identifier).get(0);

        // mock registration
        final ListenerRegistration<NotificationListenerAdapter> registration = mock(ListenerRegistration.class);
        when(this.domNotification.registerNotificationListener(listener,
            Absolute.of(ImmutableList.copyOf(listener.getSchemaPath().getPathFromRoot()))))
                .thenReturn(registration);

        // test to register listener for the first time
        this.brokerFacade.registerToListenNotification(listener);
        assertEquals("Registration was not successful", true, listener.isListening());

        // try to register for the second time
        this.brokerFacade.registerToListenNotification(listener);
        assertEquals("Registration was not successful", true, listener.isListening());

        // registrations should be invoked only once
        verify(this.domNotification, times(1)).registerNotificationListener(listener,
            Absolute.of(ImmutableList.copyOf(listener.getSchemaPath().getPathFromRoot())));

        final DOMTransactionChain transactionChain = mock(DOMTransactionChain.class);
        final DOMDataTreeWriteTransaction wTx = mock(DOMDataTreeWriteTransaction.class);
        doReturn(CommitInfo.emptyFluentFuture()).when(wTx).commit();
        when(transactionChain.newWriteOnlyTransaction()).thenReturn(wTx);
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
        final InstanceIdentifierContext<?> identifierContext = mock(InstanceIdentifierContext.class);

        when(patchContext.getData()).thenReturn(new ArrayList<>());
        doReturn(identifierContext).when(patchContext).getInstanceIdentifierContext();

        // no mount point
        when(identifierContext.getMountPoint()).thenReturn(null);

        doReturn(CommitInfo.emptyFluentFuture()).when(this.rwTransaction).commit();

        final PatchStatusContext status = this.brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

        // assert success
        assertTrue("Patch operation should be successful on server", status.isOk());
    }

    /**
     * Test Patch method on mounted device with no data.
     */
    @Test
    public void testPatchConfigurationDataWithinTransactionMount() throws Exception {
        final PatchContext patchContext = mock(PatchContext.class);
        final InstanceIdentifierContext<?> identifierContext = mock(InstanceIdentifierContext.class);
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        final DOMDataBroker mountDataBroker = mock(DOMDataBroker.class);
        final DOMDataTreeReadWriteTransaction transaction = mock(DOMDataTreeReadWriteTransaction.class);

        when(patchContext.getData()).thenReturn(new ArrayList<>());
        doReturn(identifierContext).when(patchContext).getInstanceIdentifierContext();

        // return mount point with broker
        when(identifierContext.getMountPoint()).thenReturn(mountPoint);
        when(mountPoint.getService(DOMDataBroker.class)).thenReturn(Optional.of(mountDataBroker));
        when(mountDataBroker.newReadWriteTransaction()).thenReturn(transaction);
        doReturn(CommitInfo.emptyFluentFuture()).when(transaction).commit();

        final PatchStatusContext status = this.brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

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
        final InstanceIdentifierContext<?> identifierContext = mock(InstanceIdentifierContext.class);
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        final DOMDataBroker mountDataBroker = mock(DOMDataBroker.class);
        final DOMDataTreeReadWriteTransaction transaction = mock(DOMDataTreeReadWriteTransaction.class);

        when(patchContext.getData()).thenReturn(new ArrayList<>());
        doReturn(identifierContext).when(patchContext).getInstanceIdentifierContext();
        when(identifierContext.getMountPoint()).thenReturn(mountPoint);

        // missing broker on mounted device
        when(mountPoint.getService(DOMDataBroker.class)).thenReturn(Optional.empty());

        when(mountDataBroker.newReadWriteTransaction()).thenReturn(transaction);
        doReturn(CommitInfo.emptyFluentFuture()).when(transaction).commit();

        final PatchStatusContext status = this.brokerFacade.patchConfigurationDataWithinTransaction(patchContext);

        // assert not successful operation with error
        assertNotNull(status.getGlobalErrors());
        assertEquals(1, status.getGlobalErrors().size());
        assertEquals(ErrorType.APPLICATION, status.getGlobalErrors().get(0).getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, status.getGlobalErrors().get(0).getErrorTag());

        assertFalse("Patch operation should fail on mounted device without Broker", status.isOk());
    }
}

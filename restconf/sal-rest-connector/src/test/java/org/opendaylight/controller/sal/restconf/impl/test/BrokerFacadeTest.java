/*
 * Copyright (c) 2014, 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.Broker.ConsumerSession;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.PutResult;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
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
    DOMDataBroker domDataBroker;

    @Mock
    ConsumerSession context;

    @Mock
    DOMRpcService mockRpcService;

    @Mock
    DOMMountPoint mockMountInstance;

    BrokerFacade brokerFacade = BrokerFacade.getInstance();

    NormalizedNode<?, ?> dummyNode = createDummyNode("test:module", "2014-01-09", "interfaces");
    CheckedFuture<Optional<NormalizedNode<?, ?>>,ReadFailedException> dummyNodeInFuture = wrapDummyNode(this.dummyNode);

    QName qname = TestUtils.buildQName("interfaces","test:module", "2014-01-09");

    SchemaPath type = SchemaPath.create(true, this.qname);

    YangInstanceIdentifier instanceID = YangInstanceIdentifier.builder().node(this.qname).build();

    @Mock
    DOMDataReadOnlyTransaction rTransaction;

    @Mock
    DOMDataWriteTransaction wTransaction;

    @Mock
    DOMDataReadWriteTransaction rwTransaction;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // TODO it is started before every test method
        this.brokerFacade.setDomDataBroker(this.domDataBroker);
        this.brokerFacade.setRpcService(this.mockRpcService);
        this.brokerFacade.setContext(this.context);
        when(this.domDataBroker.newReadOnlyTransaction()).thenReturn(this.rTransaction);
        when(this.domDataBroker.newWriteOnlyTransaction()).thenReturn(this.wTransaction);
        when(this.domDataBroker.newReadWriteTransaction()).thenReturn(this.rwTransaction);

        ControllerContext.getInstance().setSchemas(TestUtils.loadSchemaContext("/full-versions/test-module"));

    }

    private CheckedFuture<Optional<NormalizedNode<?, ?>>,ReadFailedException> wrapDummyNode(final NormalizedNode<?, ?> dummyNode) {
        return  Futures.immediateCheckedFuture(Optional.<NormalizedNode<?, ?>> of(dummyNode));
    }

    private CheckedFuture<Boolean,ReadFailedException> wrapExistence(final Boolean exists) {
        return  Futures.immediateCheckedFuture(exists);
    }


    /**
     * Value of this node shouldn't be important for testing purposes
     */
    private NormalizedNode<?, ?> createDummyNode(final String namespace, final String date, final String localName) {
        return Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(namespace, date, localName))).build();
    }

    @Test
    public void testReadConfigurationData() {
        when(this.rTransaction.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class))).thenReturn(
                this.dummyNodeInFuture);

        final NormalizedNode<?, ?> actualNode = this.brokerFacade.readConfigurationData(this.instanceID);

        assertSame("readConfigurationData", this.dummyNode, actualNode);
    }

    @Test
    public void testReadOperationalData() {
        when(this.rTransaction.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class))).thenReturn(
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
    public void testInvokeRpc() throws Exception {
        final DOMRpcResult expResult = mock(DOMRpcResult.class);
        final CheckedFuture<DOMRpcResult, DOMRpcException> future = Futures.immediateCheckedFuture(expResult);
        when(this.mockRpcService.invokeRpc(this.type, this.dummyNode)).thenReturn(future);

        final CheckedFuture<DOMRpcResult, DOMRpcException> actualFuture = this.brokerFacade.invokeRpc(this.type, this.dummyNode);
        assertNotNull("Future is null", actualFuture);
        final DOMRpcResult actualResult = actualFuture.get();
        assertSame("invokeRpc", expResult, actualResult);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testInvokeRpcWithNoConsumerSession() {
        this.brokerFacade.setContext(null);
        this.brokerFacade.invokeRpc(this.type, this.dummyNode);
    }

    @Ignore
    @Test
    public void testCommitConfigurationDataPut() {
        @SuppressWarnings("unchecked")
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = mock(CheckedFuture.class);

        when(this.wTransaction.submit()).thenReturn(expFuture);
        final PutResult result = this.brokerFacade.commitConfigurationDataPut((SchemaContext) null, this.instanceID,
                this.dummyNode);
        final Future<Void> actualFuture = result.getFutureOfPutData();

        assertSame("commitConfigurationDataPut", expFuture, actualFuture);

        final InOrder inOrder = inOrder(this.domDataBroker, this.wTransaction);
        inOrder.verify(this.domDataBroker).newWriteOnlyTransaction();
        inOrder.verify(this.wTransaction).put(LogicalDatastoreType.CONFIGURATION, this.instanceID, this.dummyNode);
        inOrder.verify(this.wTransaction).submit();
    }

    @Test
    public void testCommitConfigurationDataPost() {
        @SuppressWarnings("unchecked")
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = mock(CheckedFuture.class);

        when(this.rwTransaction.exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class))).thenReturn(
            wrapExistence(false));


        when(this.rwTransaction.submit()).thenReturn(expFuture);

        final CheckedFuture<Void, TransactionCommitFailedException> actualFuture = this.brokerFacade.commitConfigurationDataPost(
                (SchemaContext)null, this.instanceID, this.dummyNode);

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
        when(this.rwTransaction.exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class))).thenReturn(
                successFuture);
        try {
            // Schema context is only necessary for ensuring parent structure
            this.brokerFacade.commitConfigurationDataPost((SchemaContext)null, this.instanceID, this.dummyNode);
        } catch (final RestconfDocumentedException e) {
            assertEquals("getErrorTag", RestconfError.ErrorTag.DATA_EXISTS, e.getErrors().get(0).getErrorTag());
            throw e;
        }
    }

    @Test
    public void testCommitConfigurationDataDelete() {
        @SuppressWarnings("unchecked")
        final CheckedFuture<Void, TransactionCommitFailedException> expFuture = mock(CheckedFuture.class);

        when(this.wTransaction.submit()).thenReturn(expFuture);

        final CheckedFuture<Void, TransactionCommitFailedException> actualFuture = this.brokerFacade
                .commitConfigurationDataDelete(this.instanceID);

        assertSame("commitConfigurationDataDelete", expFuture, actualFuture);

        final InOrder inOrder = inOrder(this.domDataBroker, this.wTransaction);
        inOrder.verify(this.domDataBroker).newWriteOnlyTransaction();
        inOrder.verify(this.wTransaction).delete(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        inOrder.verify(this.wTransaction).submit();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRegisterToListenDataChanges() {
        final ListenerAdapter listener = Notificator.createListener(this.instanceID, "stream");

        final ListenerRegistration<DOMDataChangeListener> mockRegistration = mock(ListenerRegistration.class);

        when(
                this.domDataBroker.registerDataChangeListener(any(LogicalDatastoreType.class), eq(this.instanceID), eq(listener),
                        eq(DataChangeScope.BASE))).thenReturn(mockRegistration);

        this.brokerFacade.registerToListenDataChanges(LogicalDatastoreType.CONFIGURATION, DataChangeScope.BASE, listener);

        verify(this.domDataBroker).registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, this.instanceID, listener,
                DataChangeScope.BASE);

        assertEquals("isListening", true, listener.isListening());

        this.brokerFacade.registerToListenDataChanges(LogicalDatastoreType.CONFIGURATION, DataChangeScope.BASE, listener);
        verifyNoMoreInteractions(this.domDataBroker);

    }
}

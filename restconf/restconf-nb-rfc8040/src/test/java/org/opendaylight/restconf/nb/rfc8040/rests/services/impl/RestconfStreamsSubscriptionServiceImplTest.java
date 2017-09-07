/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.Notificator;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfStreamsSubscriptionServiceImplTest {

    private static final String URI = "/restconf/18/data/ietf-restconf-monitoring:restconf-state/streams/stream/"
            + "toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE";
    private static Field listenersByStreamName;

    @Mock
    private DOMDataBrokerHandler dataBrokerHandler;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private NotificationServiceHandler notificationServiceHandler;
    @Mock
    private TransactionChainHandler transactionHandler;

    private SchemaContextHandler schemaHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        final TransactionChainHandler txHandler = Mockito.mock(TransactionChainHandler.class);
        final DOMTransactionChain domTx = Mockito.mock(DOMTransactionChain.class);
        Mockito.when(this.transactionHandler.get()).thenReturn(domTx);
        Mockito.when(txHandler.get()).thenReturn(domTx);
        final DOMDataWriteTransaction wTx = Mockito.mock(DOMDataWriteTransaction.class);
        Mockito.when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        final DOMDataReadWriteTransaction rwTx = Mockito.mock(DOMDataReadWriteTransaction.class);
        final CheckedFuture<Boolean, ReadFailedException> checkedFuture = Futures.immediateCheckedFuture(Boolean.TRUE);
        Mockito.when(rwTx.exists(Mockito.any(), Mockito.any())).thenReturn(checkedFuture);
        final CheckedFuture<Void, TransactionCommitFailedException> checkedFutureEmpty =
                Futures.immediateCheckedFuture(null);
        Mockito.when(rwTx.submit()).thenReturn(checkedFutureEmpty);
        Mockito.when(domTx.newReadWriteTransaction()).thenReturn(rwTx);
        final CheckedFuture<Void, TransactionCommitFailedException> checked = Mockito.mock(CheckedFuture.class);
        Mockito.when(wTx.submit()).thenReturn(checked);
        Mockito.when(checked.checkedGet()).thenReturn(null);
        this.schemaHandler = new SchemaContextHandler(txHandler);

        final DOMDataBroker dataBroker = mock(DOMDataBroker.class);
        final ListenerRegistration<DOMDataChangeListener> listener = mock(ListenerRegistration.class);
        doReturn(dataBroker).when(this.dataBrokerHandler).get();
        doReturn(listener).when(dataBroker).registerDataChangeListener(any(), any(), any(), any());
        final MultivaluedMap<String, String> map = Mockito.mock(MultivaluedMap.class);
        final Set<Entry<String, List<String>>> set = new HashSet<>();
        Mockito.when(map.entrySet()).thenReturn(set);
        Mockito.when(this.uriInfo.getQueryParameters()).thenReturn(map);
        this.schemaHandler.onGlobalContextUpdated(
                YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles("/notifications")));
    }

    @BeforeClass
    public static void setUpBeforeTest() throws Exception {
        final Map<String, ListenerAdapter> listenersByStreamNameSetter = new HashMap<>();
        final ListenerAdapter adapter = mock(ListenerAdapter.class);
        listenersByStreamNameSetter.put(
                "data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE",
                adapter);
        listenersByStreamName = Notificator.class.getDeclaredField("dataChangeListener");

        listenersByStreamName.setAccessible(true);
        listenersByStreamName.set(Notificator.class, listenersByStreamNameSetter);
    }

    @AfterClass
    public static void setUpAfterTest() throws Exception {
        listenersByStreamName.set(Notificator.class, null);
        listenersByStreamName.set(Notificator.class, new ConcurrentHashMap<>());
        listenersByStreamName.setAccessible(false);
    }

    @Test
    public void testSubscribeToStream() throws Exception {
        final UriBuilder uriBuilder = UriBuilder.fromUri(URI);
        final ListenerAdapter createListener = Notificator.createListener(
                IdentifierCodec.deserialize("toaster:toaster/toasterStatus", this.schemaHandler.get()),
                "data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE",
                NotificationOutputType.XML);
        doReturn(uriBuilder).when(this.uriInfo).getAbsolutePathBuilder();
        final RestconfStreamsSubscriptionServiceImpl streamsSubscriptionService =
                new RestconfStreamsSubscriptionServiceImpl(this.dataBrokerHandler, this.notificationServiceHandler,
                        this.schemaHandler, this.transactionHandler);
        final NormalizedNodeContext response = streamsSubscriptionService
                .subscribeToStream(
                        "data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE",
                        this.uriInfo);
        assertEquals(
            "ws://:8181/data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE",
            response.getNewHeaders().get("Location").toString());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testSubscribeToStreamMissingDatastoreInPath() {
        final UriBuilder uriBuilder = UriBuilder.fromUri(URI);
        doReturn(uriBuilder).when(this.uriInfo).getAbsolutePathBuilder();
        final RestconfStreamsSubscriptionServiceImpl streamsSubscriptionService =
                new RestconfStreamsSubscriptionServiceImpl(this.dataBrokerHandler, this.notificationServiceHandler,
                        this.schemaHandler, this.transactionHandler);
        streamsSubscriptionService.subscribeToStream("toaster:toaster/toasterStatus/scope=ONE", this.uriInfo);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testSubscribeToStreamMissingScopeInPath() {
        final UriBuilder uriBuilder = UriBuilder.fromUri(URI);
        doReturn(uriBuilder).when(this.uriInfo).getAbsolutePathBuilder();
        final RestconfStreamsSubscriptionServiceImpl streamsSubscriptionService =
                new RestconfStreamsSubscriptionServiceImpl(this.dataBrokerHandler, this.notificationServiceHandler,
                        this.schemaHandler, this.transactionHandler);
        streamsSubscriptionService.subscribeToStream("toaster:toaster/toasterStatus/datastore=OPERATIONAL",
                this.uriInfo);
    }

}

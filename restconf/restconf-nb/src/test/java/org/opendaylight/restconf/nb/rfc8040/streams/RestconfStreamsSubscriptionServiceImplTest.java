/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertThrows;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.doReturn;
//import static org.mockito.Mockito.mock;
//
//import com.google.common.collect.ImmutableClassToInstanceMap;
//import java.net.URI;
//import javax.ws.rs.core.MultivaluedHashMap;
//import javax.ws.rs.core.UriBuilder;
//import javax.ws.rs.core.UriInfo;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnitRunner;
//import org.opendaylight.mdsal.common.api.CommitInfo;
//import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
//import org.opendaylight.mdsal.dom.api.DOMDataBroker;
//import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
//import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
//import org.opendaylight.mdsal.dom.api.DOMNotificationService;
//import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
//import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
//import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
//import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
//import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
//import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708
// .CreateDataChangeEventSubscriptionInput1.Scope;
//import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping
// .NotificationOutputType;
//import org.opendaylight.yangtools.concepts.ListenerRegistration;
//import org.opendaylight.yangtools.yang.common.ErrorTag;
//import org.opendaylight.yangtools.yang.common.ErrorType;

//@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfStreamsSubscriptionServiceImplTest extends AbstractNotificationListenerTest {
//    private static final String URI = "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream/"
//            + "toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE";
//
//    @Mock
//    private DOMDataBroker dataBroker;
//    @Mock
//    private UriInfo uriInfo;
//    @Mock
//    private DOMNotificationService notificationService;
//
//    private final DatabindProvider databindProvider = () -> DatabindContext.ofModel(MODEL_CONTEXT);
//
//    @Before
//    public void setUp() throws Exception {
//        final var wTx = mock(DOMDataTreeWriteTransaction.class);
//        doReturn(wTx).when(dataBroker).newWriteOnlyTransaction();
//        doReturn(CommitInfo.emptyFluentFuture()).when(wTx).commit();
//
//        final var dataTreeChangeService = mock(DOMDataTreeChangeService.class);
//        doReturn(mock(ListenerRegistration.class)).when(dataTreeChangeService)
//                .registerDataTreeChangeListener(any(), any());
//
//        doReturn(ImmutableClassToInstanceMap.of(DOMDataTreeChangeService.class, dataTreeChangeService))
//                .when(dataBroker).getExtensions();
//
//        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
//        doReturn(UriBuilder.fromUri("http://localhost:8181")).when(uriInfo).getBaseUriBuilder();
//        doReturn(new URI("http://127.0.0.1/" + URI)).when(uriInfo).getAbsolutePath();
//    }
//
//    @Test
//    public void testSubscribeToStreamSSE() {
//        final var listenersBroker = new ListenersBroker.ServerSentEvents();
//        listenersBroker.registerDataChangeListener(MODEL_CONTEXT, LogicalDatastoreType.OPERATIONAL,
//            IdentifierCodec.deserialize("toaster:toaster/toasterStatus", MODEL_CONTEXT), Scope.ONE,
//            NotificationOutputType.XML);
//        final var streamsSubscriptionService = new RestconfStreamsSubscriptionServiceImpl(dataBroker,
//            notificationService, databindProvider,listenersBroker);
//        final var response = streamsSubscriptionService.subscribeToStream(
//            "data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE", uriInfo);
//        assertEquals("http://localhost:8181/rests/streams"
//            + "/data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE",
//            response.getLocation().toString());
//    }
//
//    @Test
//    public void testSubscribeToStreamWS() {
//        final var listenersBroker = new ListenersBroker.WebSockets();
//        listenersBroker.registerDataChangeListener(MODEL_CONTEXT, LogicalDatastoreType.OPERATIONAL,
//            IdentifierCodec.deserialize("toaster:toaster/toasterStatus", MODEL_CONTEXT), Scope.ONE,
//            NotificationOutputType.XML);
//        final var streamsSubscriptionService = new RestconfStreamsSubscriptionServiceImpl(dataBroker,
//            notificationService, databindProvider, listenersBroker);
//        final var response = streamsSubscriptionService.subscribeToStream(
//            "data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE", uriInfo);
//        assertEquals("ws://localhost:8181/rests/streams"
//            + "/data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE",
//            response.getLocation().toString());
//    }
//
//    @Test
//    public void testSubscribeToStreamMissingDatastoreInPath() {
//        final var listenersBroker = new ListenersBroker.WebSockets();
//        final var streamsSubscriptionService = new RestconfStreamsSubscriptionServiceImpl(dataBroker,
//            notificationService, databindProvider, listenersBroker);
//        final var errors = assertThrows(RestconfDocumentedException.class,
//            () -> streamsSubscriptionService.subscribeToStream("toaster:toaster/toasterStatus/scope=ONE", uriInfo))
//            .getErrors();
//        assertEquals(1, errors.size());
//        final var error = errors.get(0);
//        assertEquals(ErrorType.APPLICATION, error.getErrorType());
//        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
//        assertEquals("Bad type of notification of sal-remote", error.getErrorMessage());
//    }
//
//    @Test
//    public void testSubscribeToStreamMissingScopeInPath() {
//        final var listenersBroker = new ListenersBroker.WebSockets();
//        final var streamsSubscriptionService = new RestconfStreamsSubscriptionServiceImpl(dataBroker,
//            notificationService, databindProvider, listenersBroker);
//        final var errors = assertThrows(RestconfDocumentedException.class,
//            () -> streamsSubscriptionService.subscribeToStream("toaster:toaster/toasterStatus/datastore=OPERATIONAL",
//                uriInfo)).getErrors();
//        assertEquals(1, errors.size());
//        final var error = errors.get(0);
//        assertEquals(ErrorType.APPLICATION, error.getErrorType());
//        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
//        assertEquals("Bad type of notification of sal-remote", error.getErrorMessage());
//    }
}

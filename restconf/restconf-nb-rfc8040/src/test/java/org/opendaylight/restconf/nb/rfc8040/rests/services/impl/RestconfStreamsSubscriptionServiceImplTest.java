/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.collect.ImmutableClassToInstanceMap;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.util.SimpleUriInfo;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfStreamsSubscriptionServiceImplTest {

    private static final String URI = "/restconf/18/data/ietf-restconf-monitoring:restconf-state/streams/stream/"
            + "toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE";

    @Mock
    private DOMDataBrokerHandler dataBrokerHandler;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private NotificationServiceHandler notificationServiceHandler;

    private TransactionChainHandler transactionHandler;
    private SchemaContextHandler schemaHandler;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws FileNotFoundException, URISyntaxException {
        MockitoAnnotations.initMocks(this);

        final DOMTransactionChain domTx = mock(DOMTransactionChain.class);
        final DOMDataTreeWriteTransaction wTx = mock(DOMDataTreeWriteTransaction.class);
        when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        final DOMDataTreeReadWriteTransaction rwTx = mock(DOMDataTreeReadWriteTransaction.class);
        when(rwTx.exists(any(), any())).thenReturn(immediateTrueFluentFuture());
        doReturn(CommitInfo.emptyFluentFuture()).when(rwTx).commit();
        when(domTx.newReadWriteTransaction()).thenReturn(rwTx);
        doReturn(CommitInfo.emptyFluentFuture()).when(wTx).commit();

        final DOMDataBroker dataBroker = mock(DOMDataBroker.class);
        doReturn(domTx).when(dataBroker).createTransactionChain(any());

        transactionHandler = new TransactionChainHandler(dataBroker);
        schemaHandler = SchemaContextHandler.newInstance(transactionHandler, mock(DOMSchemaService.class));

        DOMDataTreeChangeService dataTreeChangeService = mock(DOMDataTreeChangeService.class);
        doReturn(mock(ListenerRegistration.class)).when(dataTreeChangeService)
                .registerDataTreeChangeListener(any(), any());

        doReturn(ImmutableClassToInstanceMap.of(DOMDataTreeChangeService.class, dataTreeChangeService))
                .when(dataBroker).getExtensions();

        doReturn(dataBroker).when(this.dataBrokerHandler).get();

        final MultivaluedMap<String, String> map = mock(MultivaluedMap.class);
        final Set<Entry<String, List<String>>> set = new HashSet<>();
        when(map.entrySet()).thenReturn(set);
        when(this.uriInfo.getQueryParameters()).thenReturn(map);
        final UriBuilder baseUriBuilder = new LocalUriInfo().getBaseUriBuilder();
        when(uriInfo.getBaseUri()).thenReturn(baseUriBuilder.build());
        when(uriInfo.getBaseUriBuilder()).thenReturn(baseUriBuilder);
        final URI uri = new URI("http://127.0.0.1/" + URI);
        when(uriInfo.getAbsolutePath()).thenReturn(uri);
        this.schemaHandler.onGlobalContextUpdated(
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/notifications")));
    }

    private static class LocalUriInfo extends SimpleUriInfo {

        LocalUriInfo() {
            super("/");
        }

        @Override
        public URI getBaseUri() {
            return UriBuilder.fromUri("http://localhost:8181").build();
        }
    }

    @BeforeClass
    public static void setUpBeforeTest() {
        final Map<String, ListenerAdapter> listenersByStreamNameSetter = new HashMap<>();
        final ListenerAdapter adapter = mock(ListenerAdapter.class);
        final YangInstanceIdentifier yiid = mock(YangInstanceIdentifier.class);
        final YangInstanceIdentifier.PathArgument lastPathArgument = mock(YangInstanceIdentifier.PathArgument.class);
        final QName qname = QName.create("toaster", "2009-11-20", "toasterStatus");
        Mockito.when(adapter.getPath()).thenReturn(yiid);
        Mockito.when(adapter.getOutputType()).thenReturn("JSON");
        Mockito.when(yiid.getLastPathArgument()).thenReturn(lastPathArgument);
        Mockito.when(lastPathArgument.getNodeType()).thenReturn(qname);
        listenersByStreamNameSetter.put(
                "data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE",
                adapter);
        ListenersBroker.getInstance().setDataChangeListeners(listenersByStreamNameSetter);
    }

    @AfterClass
    public static void setUpAfterTest() {
        ListenersBroker.getInstance().setDataChangeListeners(Collections.emptyMap());
    }

    @Test
    public void testSubscribeToStream() {
        final UriBuilder uriBuilder = UriBuilder.fromUri(URI);
        ListenersBroker.getInstance().registerDataChangeListener(
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
        assertEquals("ws://localhost:8181/" + RestconfConstants.BASE_URI_PATTERN
                + "/data-change-event-subscription/toaster:toaster/toasterStatus/"
                + "datastore=OPERATIONAL/scope=ONE", response.getNewHeaders().get("Location").toString());
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

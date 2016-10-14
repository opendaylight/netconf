/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.services.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.handlers.DOMDataBrokerHandler;
import org.opendaylight.yangtools.concepts.ListenerRegistration;

public class RestconfStreamsSubscriptionServiceImplTest {

    private static final String uri =
            "/restconf/17/data/ietf-restconf-monitoring:restconf-state/streams/stream/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE";
    private static Field listenersByStreamName;

    @Mock
    private DOMDataBrokerHandler dataBrokerHandler;
    @Mock
    private UriInfo uriInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        final DOMDataBroker dataBroker = mock(DOMDataBroker.class);
        final ListenerRegistration<DOMDataChangeListener> listener = mock(ListenerRegistration.class);
        doReturn(dataBroker).when(this.dataBrokerHandler).get();
        doReturn(listener).when(dataBroker).registerDataChangeListener(any(), any(), any(), any());
    }

    @BeforeClass
    public static void setUpBeforeTest() throws Exception {
        final Map<String, ListenerAdapter> listenersByStreamNameSetter = new HashMap<>();
        final ListenerAdapter adapter = mock(ListenerAdapter.class);
        doReturn(false).when(adapter).isListening();
        listenersByStreamNameSetter.put("toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE", adapter);
        listenersByStreamName = Notificator.class.getDeclaredField("listenersByStreamName");

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
    public void testSubscribeToStream() {
        final UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        doReturn(uriBuilder).when(this.uriInfo).getAbsolutePathBuilder();
        final RestconfStreamsSubscriptionServiceImpl streamsSubscriptionService = new RestconfStreamsSubscriptionServiceImpl(this.dataBrokerHandler);
        final Response response = streamsSubscriptionService.subscribeToStream("toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE", this.uriInfo);
        assertEquals(200, response.getStatus());
        assertEquals("ws://:8181/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE", response.getHeaderString("Location"));
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testSubscribeToStreamMissingDatastoreInPath() {
        final UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        doReturn(uriBuilder).when(this.uriInfo).getAbsolutePathBuilder();
        final RestconfStreamsSubscriptionServiceImpl streamsSubscriptionService = new RestconfStreamsSubscriptionServiceImpl(this.dataBrokerHandler);
        final Response response = streamsSubscriptionService.subscribeToStream("toaster:toaster/toasterStatus/scope=ONE", this.uriInfo);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testSubscribeToStreamMissingScopeInPath() {
        final UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        doReturn(uriBuilder).when(this.uriInfo).getAbsolutePathBuilder();
        final RestconfStreamsSubscriptionServiceImpl streamsSubscriptionService = new RestconfStreamsSubscriptionServiceImpl(this.dataBrokerHandler);
        final Response response = streamsSubscriptionService.subscribeToStream("toaster:toaster/toasterStatus/datastore=OPERATIONAL", this.uriInfo);
    }

}

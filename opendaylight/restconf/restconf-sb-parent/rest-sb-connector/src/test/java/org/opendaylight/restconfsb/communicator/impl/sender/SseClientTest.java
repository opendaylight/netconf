/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Request;
import java.util.concurrent.Executors;
import org.atmosphere.wasync.transport.SSETransport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;

public class SseClientTest {

    private static final String SSE_1 = "data1";
    private static final String SSE_2 = "data2";
    private static final String SSE_3 = "data3";
    @Mock
    private AsyncHttpClient httpClient;
    @Mock
    private AsyncHttpClient.BoundRequestBuilder requestBuilder;
    @Mock
    private SseListener listener;
    @Mock
    private HttpResponseBodyPart bodyPart1;
    @Mock
    private HttpResponseBodyPart bodyPart2;
    @Mock
    private HttpResponseBodyPart bodyPart3;
    @Mock
    private ListenableFuture future;
    private SseClient client;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(false).when(httpClient).isClosed();
        doNothing().when(httpClient).close();

        doReturn(requestBuilder).when(httpClient).prepareRequest(any(Request.class));
        doReturn(future).when(requestBuilder).execute(any(SSETransport.class));
        doReturn(true).when(future).cancel(false);

        doReturn(getSseBytes(SSE_1)).when(bodyPart1).getBodyPartBytes();
        doReturn(getSseBytes(SSE_2)).when(bodyPart2).getBodyPartBytes();
        doReturn(getSseBytes(SSE_3)).when(bodyPart3).getBodyPartBytes();
        client = new SseClient(httpClient, Executors.newSingleThreadScheduledExecutor(), 5);
    }

    @Test(timeout = 20000)
    public void testSubscribeToStream() throws Exception {
        client.subscribeToStream("http://localhost:8181/restconf/streams/base");
        client.registerListener(listener);
        final ArgumentCaptor<SSETransport> captor = ArgumentCaptor.forClass(SSETransport.class);
        verify(requestBuilder).execute(captor.capture());

        captor.getValue().onBodyPartReceived(bodyPart1);
        captor.getValue().onBodyPartReceived(bodyPart2);
        captor.getValue().onBodyPartReceived(bodyPart3);

        final InOrder order = inOrder(listener);
        order.verify(listener).onMessage(SSE_1);
        order.verify(listener).onMessage(SSE_2);
        order.verify(listener).onMessage(SSE_3);
    }

    @Test(timeout = 20000)
    public void testReconnect() throws Exception {
        client.subscribeToStream("http://localhost:8181/restconf/streams/base");
        client.registerListener(listener);
        final ArgumentCaptor<SSETransport> captor = ArgumentCaptor.forClass(SSETransport.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onThrowable(new Exception("Connection refused"));
        verify(requestBuilder).execute(any(SSETransport.class));
        captor.getValue().onBodyPartReceived(bodyPart1);
        verify(requestBuilder, timeout(10000).times(2)).execute(any(SSETransport.class));
        verify(listener).onMessage(SSE_1);
    }

    @Test(timeout = 20000)
    public void testCloseSubscription() throws Exception {
        client.subscribeToStream("http://localhost:8181/restconf/streams/base");
        final ListenerRegistration<SseListener> reg = client.registerListener(listener);
        final ArgumentCaptor<SSETransport> captor = ArgumentCaptor.forClass(SSETransport.class);
        verify(requestBuilder).execute(captor.capture());

        captor.getValue().onBodyPartReceived(bodyPart1);
        captor.getValue().onBodyPartReceived(bodyPart2);
        reg.close();
        captor.getValue().onBodyPartReceived(bodyPart3);

        final InOrder order = inOrder(listener);
        order.verify(listener).onMessage(SSE_1);
        order.verify(listener).onMessage(SSE_2);
        order.verify(listener, never()).onMessage(SSE_3);
    }

    @Test(timeout = 20000)
    public void testClose() throws Exception {
        testSubscribeToStream();
        client.close();
        verify(httpClient).close();
        verify(future).cancel(false);
    }

    private static byte[] getSseBytes(final String message) {
        return ("data:" + message).getBytes();
    }
}
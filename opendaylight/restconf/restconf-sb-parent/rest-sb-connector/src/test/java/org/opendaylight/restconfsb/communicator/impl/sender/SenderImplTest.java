/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.Request;

public class SenderImplTest {

    private static final String errorResponseBody = "error";
    @Mock
    private AsyncHttpClient client;
    @Mock
    private AsyncHttpClient.BoundRequestBuilder requestBuilder;
    @Mock
    private Response response200;
    @Mock
    private Response response201;
    @Mock
    private Response response204;
    @Mock
    private Response response404;
    @Mock
    private SseClient sseClient;
    private SenderImpl sender;
    private final String path = "/data/cont";
    private ByteArrayInputStream responseBodyStream;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final String restconfUrl = "http://localhost:8181/restconf";
        sender = new SenderImpl(client, sseClient, restconfUrl);
        doReturn(requestBuilder).when(client).prepareGet(restconfUrl + path);
        doReturn(requestBuilder).when(client).preparePut(restconfUrl + path);
        doReturn(requestBuilder).when(client).preparePost(restconfUrl + path);
        doReturn(requestBuilder).when(client).preparePatch(restconfUrl + path);
        doReturn(requestBuilder).when(client).prepareDelete(restconfUrl + path);
        doReturn(requestBuilder).when(client).prepareHead(restconfUrl + path);
        doReturn(requestBuilder).when(requestBuilder).addHeader(HttpHeaders.ACCEPT, Request.RestconfMediaType.XML_DATA.getHeaderValue());
        doReturn(requestBuilder).when(requestBuilder).addHeader(HttpHeaders.CONTENT_TYPE, Request.RestconfMediaType.XML_DATA.getHeaderValue());
        doReturn(requestBuilder).when(requestBuilder).addHeader(HttpHeaders.ACCEPT, Request.RestconfMediaType.XML_OPERATION.getHeaderValue());
        doReturn(requestBuilder).when(requestBuilder).addHeader(HttpHeaders.CONTENT_TYPE, Request.RestconfMediaType.XML_OPERATION.getHeaderValue());
        doReturn(requestBuilder).when(requestBuilder).setBody(any(String.class));
        doReturn(null).when(requestBuilder).execute(any(AsyncCompletionHandler.class));
        doReturn(200).when(response200).getStatusCode();
        responseBodyStream = new ByteArrayInputStream("success".getBytes());
        doReturn(responseBodyStream).when(response200).getResponseBodyAsStream();
        doReturn(201).when(response201).getStatusCode();
        doReturn(null).when(response201).getResponseBodyAsStream();
        doReturn(204).when(response204).getStatusCode();
        doReturn(404).when(response404).getStatusCode();
        doReturn(errorResponseBody).when(response404).getResponseBody();
    }

    @Test
    public void testGet() throws Exception {
        final ListenableFuture<InputStream> result = sender.get(Request.createRequestWithoutBody(path, Request.RestconfMediaType.XML_DATA));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response200);
        Assert.assertEquals(responseBodyStream, result.get());
    }

    @Test
    public void testPostOperation() throws Exception {
        final ListenableFuture<InputStream> result = sender.post(Request.createRequestWithBody(path, "body", Request.RestconfMediaType.XML_OPERATION));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response200);
        Assert.assertEquals(responseBodyStream, result.get());
    }

    @Test
    public void testPostResource() throws Exception {
        final ListenableFuture<InputStream> result = sender.post(Request.createRequestWithBody(path, "body", Request.RestconfMediaType.XML_DATA));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response201);
        Assert.assertEquals(null, result.get());
    }

    @Test
    public void testPatch() throws Exception {
        final ListenableFuture<Void> result = sender.patch(Request.createRequestWithBody(path, "body", Request.RestconfMediaType.XML_DATA));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response201);
        Assert.assertEquals(null, result.get());
    }

    @Test
    public void testPutReplace() throws Exception {
        final ListenableFuture<Void> result = sender.put(Request.createRequestWithBody(path, "body", Request.RestconfMediaType.XML_DATA));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response200);
        result.get();
    }

    @Test
    public void testPutCreate() throws Exception {
        final ListenableFuture<Void> result = sender.put(Request.createRequestWithBody(path, "body", Request.RestconfMediaType.XML_DATA));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response201);
        result.get();
    }

    @Test
    public void testDelete() throws Exception {
        final ListenableFuture<Void> result = sender.delete(Request.createRequestWithBody(path, "body", Request.RestconfMediaType.XML_DATA));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response204);
        result.get();
    }

    @Test
    public void testHead() throws Exception {
        final ListenableFuture<Void> result = sender.head(Request.createRequestWithoutBody(path, Request.RestconfMediaType.XML_DATA));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response200);
        result.get();
    }

    @Test
    public void testError() throws Exception {
        final ListenableFuture<InputStream> result = sender.get(Request.createRequestWithoutBody(path, Request.RestconfMediaType.XML_DATA));
        final ArgumentCaptor<AsyncCompletionHandler> captor = ArgumentCaptor.forClass(AsyncCompletionHandler.class);
        verify(requestBuilder).execute(captor.capture());
        captor.getValue().onCompleted(response404);
        try {
            result.get();
            Assert.fail("Expected ExecutionException");
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            Assert.assertTrue(cause instanceof HttpException);
            final HttpException httpException = (HttpException) cause;
            Assert.assertEquals(response404.getStatusCode(), httpException.getStatus());
            Assert.assertEquals(response404.getResponseBody(), httpException.getMsg());
        }
    }
}
/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.restconfsb.communicator.api.http.ConnectionListener;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;

public class SenderImpl implements Sender {

    private static final NoResponseStrategy NO_RESPONSE_STRATEGY = new NoResponseStrategy();
    private static final ResponseStrategy RESPONSE_STRATEGY = new ResponseStrategy();

    private final String endpoint;
    private final AsyncHttpClient client;
    private final SseClient sseClient;
    private final List<ConnectionListener> listeners = new ArrayList<>();

    private boolean connected;

    SenderImpl(final AsyncHttpClient client, final SseClient sseClient, final String url) {
        this.endpoint = url;
        this.client = client;
        this.connected = true;
        this.sseClient = sseClient;
    }

    @Override
    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public ListenableFuture<InputStream> get(final Request request) {
        final String url = createUrl(request);

        final AsyncHttpClient.BoundRequestBuilder requestBuilder = client.prepareGet(url);

        return executeHttpRequestStream(request, requestBuilder);
    }


    @Override
    public ListenableFuture<InputStream> post(final Request request) {
        final String url = createUrl(request);
        final AsyncHttpClient.BoundRequestBuilder requestBuilder = client.preparePost(url);

        return executeHttpRequestStream(request, requestBuilder);
    }

    @Override
    public ListenableFuture<Void> patch(final Request request) {
        final String url = createUrl(request);
        final AsyncHttpClient.BoundRequestBuilder requestBuilder = client.preparePatch(url);

        return executeHttpRequestVoid(request, requestBuilder);
    }

    @Override
    public ListenableFuture<Void> put(final Request request) {
        final String url = createUrl(request);
        final AsyncHttpClient.BoundRequestBuilder requestBuilder = client.preparePut(url);
        return executeHttpRequestVoid(request, requestBuilder);
    }

    @Override
    public ListenableFuture<Void> delete(final Request request) {
        final String url = createUrl(request);
        final AsyncHttpClient.BoundRequestBuilder requestBuilder = client.prepareDelete(url);

        return executeHttpRequestVoid(request, requestBuilder);
    }

    @Override
    public ListenableFuture<Void> head(final Request request) {
        final String url = createUrl(request);
        final AsyncHttpClient.BoundRequestBuilder requestBuilder = client.prepareHead(url);

        return executeHttpRequestVoid(request, requestBuilder);
    }

    @Override
    public synchronized ListenerRegistration<ConnectionListener> registerConnectionListener(final ConnectionListener listener) {
        Preconditions.checkNotNull(listener);
        listeners.add(listener);
        return new ListenerRegistration<ConnectionListener>() {
            @Override
            public void close() {
                synchronized (SenderImpl.this) {
                    listeners.remove(listener);
                }
            }

            @Override
            public ConnectionListener getInstance() {
                return listener;
            }
        };
    }

    @Override
    public ListenerRegistration<SseListener> registerSseListener(final SseListener listener) {
        return sseClient.registerListener(listener);
    }

    @Override
    public void subscribeToStream(final String streamUrl) {
        sseClient.subscribeToStream(streamUrl);
    }

    private String createUrl(final Request request) {
        return endpoint + request.getPath();
    }

    private ListenableFuture<Void> executeHttpRequestVoid(final Request request, final AsyncHttpClient.BoundRequestBuilder requestBuilder) {
        final SettableFuture<Void> future = SettableFuture.create();
        final AsyncCompletionHandler<Response> responseHandler = createNoResponseHandler(future);
        preExecutionSettings(requestBuilder, request);
        requestBuilder.execute(responseHandler);
        return future;
    }

    private ListenableFuture<InputStream> executeHttpRequestStream(final Request request, final AsyncHttpClient.BoundRequestBuilder requestBuilder) {
        final SettableFuture<InputStream> future = SettableFuture.create();
        final AsyncCompletionHandler<Response> responseHandler = createResponseHandler(future);
        preExecutionSettings(requestBuilder, request);
        requestBuilder.execute(responseHandler);
        return future;
    }

    private void preExecutionSettings(final AsyncHttpClient.BoundRequestBuilder requestBuilder, final Request request) {
        requestBuilder.addHeader(HttpHeaders.ACCEPT, request.getType().getHeaderValue());
        requestBuilder.addHeader(HttpHeaders.CONTENT_TYPE, request.getType().getHeaderValue());
        final String body = request.getBody();
        if (body != null) {
            requestBuilder.setBody(body);
        }
    }

    @Override
    public void close() throws Exception {
        client.closeAsynchronously();
        sseClient.close();
    }

    private class ResponseHandler<T> extends AsyncCompletionHandler<Response> {

        private final SettableFuture<T> future;

        private final SuccessfulResponseStrategy<T> strategy;

        public ResponseHandler(final SettableFuture<T> future, final SuccessfulResponseStrategy<T> strategy) {
            this.future = future;
            this.strategy = strategy;
        }

        @Override
        public Response onCompleted(final Response response) throws Exception {
            if (!connected) {
                for (final ConnectionListener listener : listeners) {
                    listener.onConnectionReestablished();
                }
                connected = true;
            }
            final int status = response.getStatusCode();
            if (isSuccessful(status)) {
                future.set(strategy.handle(response));
            } else {
                setHttpException(status, response.getResponseBody(), future);
            }
            return response;
        }

        @Override
        public void onThrowable(final Throwable t) {
            connected = false;
            for (final ConnectionListener listener : listeners) {
                listener.onConnectionFailed(t);
            }
            future.setException(t);
        }

    }

    private ResponseHandler<Void> createNoResponseHandler(final SettableFuture<Void> future) {
        return new ResponseHandler<>(future, NO_RESPONSE_STRATEGY);
    }

    private ResponseHandler<InputStream> createResponseHandler(final SettableFuture<InputStream> future) {
        return new ResponseHandler<>(future, RESPONSE_STRATEGY);
    }

    private static void setHttpException(final int status, final String response, final SettableFuture<?> future) {
        final HttpException exception;
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            exception = new NotFoundException(response);
        } else {
            exception = new HttpException(status, response);
        }
        future.setException(exception);
    }

    private static boolean isSuccessful(final int status) {
        return status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_NO_CONTENT ||
                status == HttpURLConnection.HTTP_CREATED;
    }

    private interface SuccessfulResponseStrategy<T> {

        T handle(Response response) throws IOException;
    }

    private static class NoResponseStrategy implements SuccessfulResponseStrategy<Void> {

        @Override
        public Void handle(final Response response) {
            return null;
        }
    }

    private static class ResponseStrategy implements SuccessfulResponseStrategy<InputStream> {

        @Override
        public InputStream handle(final Response response) throws IOException {
            return response.getResponseBodyAsStream();
        }
    }
}

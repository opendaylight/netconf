/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api.http;

import com.google.common.util.concurrent.ListenableFuture;
import java.io.InputStream;
import org.opendaylight.yangtools.concepts.ListenerRegistration;

/**
 * Sender sends HTTP requests. In case of unsuccessful call, returns {@link HttpException} wrapped in future
 * ExecutionException.
 */
public interface Sender extends AutoCloseable {

    String getEndpoint();

    /**
     * HTTP GET to url specified in request
     *
     * @param request request
     * @return response body
     */
    ListenableFuture<InputStream> get(Request request);

    /**
     * HTTP POST with body and url specified in request
     *
     * @param request request
     * @return response body
     */
    ListenableFuture<InputStream> post(Request request);

    /**
     * HTTP PATCH with body and url specified in request
     *
     * @param request request
     * @return empty response
     */
    ListenableFuture<Void> patch(Request request);

    /**
     * HTTP PUT with body and url specified in request
     *
     * @param request request
     * @return empty response
     */
    ListenableFuture<Void> put(Request request);

    /**
     * HTTP DELETE to url specified in request
     *
     * @param request request
     * @return empty response
     */
    ListenableFuture<Void> delete(Request request);

    /**
     * HTTP HEAD to url specified in request
     *
     * @param request request
     * @return empty response
     */
    ListenableFuture<Void> head(Request request);

    /**
     * Registers listener which is notified about connection changes.
     *
     * @param listener listener
     * @return listener registration
     */
    ListenerRegistration<ConnectionListener> registerConnectionListener(ConnectionListener listener);

    /**
     * Registers Server sent events listener. It will receive sse messages.
     *
     * @param listener listener
     * @return listener registration
     */
    ListenerRegistration<SseListener> registerSseListener(SseListener listener);

    /**
     * Subscribes sender to device sse stream. User then can use {@link Sender#registerSseListener(SseListener)} to receive
     * events from this stream.
     *
     * @param streamUrl stream relative path, e.g. /streams/stream/NETCONF
     */
    void subscribeToStream(String streamUrl);
}

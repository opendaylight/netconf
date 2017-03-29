/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.DefaultClient;
import org.atmosphere.wasync.impl.DefaultOptions;
import org.atmosphere.wasync.impl.DefaultOptionsBuilder;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server sent events client. Notifies registered {@link SseListener} instances.
 */
class SseClient {
    private static final Logger LOG = LoggerFactory.getLogger(SseClient.class);

    private static final NettyAsyncHttpProviderConfig NETTY_CONFIG = new NettyAsyncHttpProviderConfig();

    private final Map<String, Socket> subscriptions = new HashMap<>();
    private final List<SseListener> listeners = new ArrayList<>();

    static {
        final NioClientSocketChannelFactory factory = new NioClientSocketChannelFactory();
        NETTY_CONFIG.setSocketChannelFactory(factory);
    }

    /**
     * Http client shared by all subscriptions
     */
    private final AsyncHttpClient client;

    /**
     * Executor used for scheduling reconnect attempts
     */
    private final ScheduledExecutorService reconnectingExecutor;
    /**
     * Running streams handler attempts to reconnect if connection is lost
     */
    private boolean running;
    /**
     * Interval that how often it tries to reconnect if connection is lost
     */
    private final long reconnectInterval;

    /**
     * @param client http client
     * @param reconnectingExecutor executor service used to schedule reconnect attempts in case of failure
     */
    SseClient(final AsyncHttpClient client, final ScheduledExecutorService reconnectingExecutor, final long reconnectInterval) {
        this.reconnectingExecutor = reconnectingExecutor;
        running = true;
        this.client = client;
        this.reconnectInterval = reconnectInterval;
    }

    /**
     * Subscribes to stream specified by url. In case of connection loss, handler tries
     * to reconnect to all subscribed event streams in regular time intervals.
     *
     * @param streamUrl absolute stream url
     * @return stream registration. Can be used for cancelling subscrition.
     */
    synchronized AutoCloseable subscribeToStream(final String streamUrl) {
        final DefaultClient sseClient = new DefaultClient();
        final DefaultOptionsBuilder optionsBuilder = (DefaultOptionsBuilder) sseClient.newOptionsBuilder();
        final DefaultOptions options = optionsBuilder
                .runtime(client, true)
                .reconnect(true)
                .build();

        final RequestBuilder requestbuilder = sseClient.newRequestBuilder();
        requestbuilder.method(Request.METHOD.GET);
        requestbuilder.uri(streamUrl);
        requestbuilder.transport(Request.TRANSPORT.SSE);
        final Request request = requestbuilder.build();
        final Socket socket = sseClient.create(options);
        subscriptions.put(streamUrl, socket);
        initSocket(streamUrl, socket);
        try {
            socket.open(request);
        } catch (final IOException e) {
            LOG.warn("Stream subscription error: {}", e);
        }
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                closeSubscription(streamUrl);
                subscriptions.remove(streamUrl);
            }
        };
    }

    /**
     * Init socket callback functions.
     *
     * @param url    stream url
     * @param socket socket
     */
    private void initSocket(final String url, final Socket socket) {
        socket.on(new Function<TimeoutException>() {
            @Override
            public void on(final TimeoutException e) {
                LOG.error("Timeout exception", e);
            }
        }).on(Event.MESSAGE, new Function<String>() {
            @Override
            public void on(final String t) {
                handleNotification(t);
            }
        }).on(Event.OPEN, new Function<String>() {
            @Override
            public void on(final String t) {
                LOG.info("Subscribed to {} stream", url);
            }
        }).on(Event.ERROR, new Function<String>() {
            @Override
            public void on(final String s) {
                LOG.warn("Error: {}", s);
                if (running) {
                    reconnect(url);
                }
            }
        });
    }

    private void reconnect(final String url) {
        reconnectingExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                LOG.info("Reconecting to stream {}", url);
                subscribeToStream(url);
            }
        }, reconnectInterval, TimeUnit.SECONDS);
        closeSubscription(url);
    }

    private void closeSubscription(final String url) {
        subscriptions.get(url).close();
    }

    private void handleNotification(final String message) {
        for (final SseListener listener : listeners) {
            listener.onMessage(message);
        }

    }

    /**
     * Registers listener which will be notified about received Server sent events messages.
     *
     * @param listener listener
     * @return listener registration
     */
    synchronized ListenerRegistration<SseListener> registerListener(final SseListener listener) {
        listeners.add(listener);
        return new ListenerRegistration<SseListener>() {
            @Override
            public void close() {
                synchronized (SseClient.this) {
                    listeners.remove(listener);
                }
            }

            @Override
            public SseListener getInstance() {
                return listener;
            }
        };
    }

    void close() {
        running = false;
        for (final Socket socket : subscriptions.values()) {
            socket.close();
        }
        subscriptions.clear();
        client.close();
    }
}

/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;

/**
 * Web-socket servlet listening on ws or wss schemas for created data-change-event or notification streams.
 */
@Singleton
public final class WebSocketInitializer extends WebSocketServlet {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final transient WebSocketFactory creator;
    private final int idleTimeoutMillis;

    /**
     * Creation of the web-socket initializer.
     *
     * @param scheduledThreadPool    ODL thread pool used for fetching of scheduled executors.
     * @param configuration          Web-socket configuration holder.
     */
    @Inject
    public WebSocketInitializer(final ScheduledThreadPool scheduledThreadPool,
            final ListenersBroker listenersBroker, final StreamsConfiguration configuration) {
        creator = new WebSocketFactory(scheduledThreadPool.getExecutor(), listenersBroker,
            configuration.maximumFragmentLength(), configuration.heartbeatInterval());
        idleTimeoutMillis = configuration.idleTimeout();
    }

    /**
     * Configuration of the web-socket factory - idle timeout and specified factory object.
     *
     * @param factory Configurable web-socket factory.
     */
    @Override
    public void configure(final WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(idleTimeoutMillis);
        factory.setCreator(creator);
    }

    @java.io.Serial
    @SuppressWarnings("static-method")
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        throwNSE();
    }

    @java.io.Serial
    @SuppressWarnings("static-method")
    private void writeObject(final ObjectOutputStream out) throws IOException {
        throwNSE();
    }

    private static void throwNSE() throws NotSerializableException {
        throw new NotSerializableException(WebSocketInitializer.class.getName());
    }
}

/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * SSE Event producer interface.
 */
@NonNullByDefault
public interface EventStreamService {
    /**
     * Accepts SSE stream request using request URI as stream descriptor. Stream request result is delivered through
     * invocation of corresponding method on callback object.
     *
     * <p>
     * If request is accepted then {@link StartCallback#onStreamStarted(StreamControl)} method invoked with
     * a {@link Registration} instance, to be used for stream termination. Stream event to be applied on
     * {@link EventStreamListener} instance provided.
     *
     * <p>
     * If request is declined then {@link StartCallback#onStartFailure(Exception)} method invoked with
     * {@link Exception} describing the decline reason.
     *
     * @param requestUri stream request URI
     * @param listener SSE event consumer
     * @param callback SSE stream request callback
     */
    void startEventStream(String requestUri, EventStreamListener listener, StartCallback callback);

    /**
     * Invoked when the request to attach to an event stream finishes.
     */
    interface StartCallback {
        /**
         * Invoked when the stream has been started. Are responsible for ownership of provided {@link StreamControl}.
         *
         * @param streamControl a {@link StreamControl}
         */
        void onStreamStarted(StreamControl streamControl);

        /**
         * Invoked when a stream fails to start.
         *
         * @param cause cause of the failure to start
         */
        void onStartFailure(Exception cause);
    }

    /**
     * An interface controlling a started stream.
     */
    interface StreamControl {
        /**
         * Close the stream.
         */
        void close();
    }
}

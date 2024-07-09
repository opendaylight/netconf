/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * SSE Event producer interface.
 */
public interface EventStreamService {

    /**
     * Accepts SSE stream request using request URI as stream descriptor. Stream request result is delivered through
     * invocation of corresponding method on callback object.
     *
     * <p>
     * If request is accepted then {@link FutureCallback#onSuccess(Object)} method invoked with
     * {@link Registration} instance, to be used for stream termination. Stream event to be applied on
     * {@link EventStreamListener} instance provided.
     *
     * <p>
     * If request is declined then {@link FutureCallback#onFailure(Throwable)} method invoked with
     * {@link Throwable} describing the decline reason.
     *
     * @param requestUri stream request URI
     * @param listener SSE event consumer
     * @param callback SSE stream request callback
     */
    void startEventStream(@NonNull String requestUri, @NonNull EventStreamListener listener,
        @NonNull FutureCallback<Registration> callback);
}

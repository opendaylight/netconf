/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import com.ning.http.client.AsyncHttpClient;
import javax.annotation.Nullable;

public interface HttpClientProvider {
    /**
     * Creates AsyncHttpClient set according to provided parameters
     *
     * @param requestTimeout request timeout
     * @param principal      username
     * @param password       password
     * @param idleTimeout    the maximum time in millisecond an {@link com.ning.http.client.AsyncHttpClient} will keep connection
     *                       idle in pool
     * @param requestTimeout the maximum time in millisecond an {@link com.ning.http.client.AsyncHttpClient} waits until the
     *                       response is completed.
     * @param connectTimeout the maximum time in millisecond an {@link com.ning.http.client.AsyncHttpClient} can wait when
     *                       connecting to a remote host
     * @param followRedirect true if HTTP redirect enabled
     * @return client
     */
    AsyncHttpClient createHttpClient(@Nullable final String principal, @Nullable final String password, final int idleTimeout,
                                     final int requestTimeout, final int connectTimeout, final boolean followRedirect);

}

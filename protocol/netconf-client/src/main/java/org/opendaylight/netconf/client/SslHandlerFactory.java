/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.handler.ssl.SslHandler;
import java.util.Set;

public interface SslHandlerFactory {
    /**
     * This factory is used by the TLS client to create SslHandler that will be added into the channel pipeline when
     * the channel is active.
     */
    default SslHandler createSslHandler() {
        return createSslHandler(Set.of());
    }

    SslHandler createSslHandler(Set<String> allowedKeys);
}

/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;

/**
 * Extension interface for external service integration with TLS transport.
 * Used to build {@link TLSServer} and {@link TLSServer} instances.
 */
@FunctionalInterface
public interface SslHandlerFactory {
    /**
     * Builds {@link SslHandler} instance for given {@link Channel}.
     *
     * @param channel channel
     * @return SslHandler instance
     */
    SslHandler createSslHandler(Channel channel);
}

/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelHandler;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Custom Auth Handler factory.
 */
@FunctionalInterface
public interface AuthHandlerFactory {

    /**
     * Builds {@link ChannelHandler} instance to serve request authorization.
     *
     * @return auth handler instance
     */
    @NonNull ChannelHandler newAuthHandler();
}

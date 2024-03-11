/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.ChannelHandler;

/**
 * HTTP Channel initializer interface.
 */
interface HttpChannelInitializer extends ChannelHandler {

    /**
     * Returns future indicating channel initialization completion.
     *
     * @return listenable future associated with this channel initializer.
     */
    ListenableFuture<Void> completeFuture();

}

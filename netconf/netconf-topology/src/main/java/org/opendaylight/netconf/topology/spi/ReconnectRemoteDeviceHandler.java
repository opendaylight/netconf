/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import io.netty.util.concurrent.EventExecutor;
import java.util.concurrent.TimeUnit;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice.EmptySchemaContextException;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A device handler which schedules automatic reconnection of remote device in case it ends up failing with an
 * {@link EmptySchemaContextException}.
 */
final class ReconnectRemoteDeviceHandler implements RemoteDeviceHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ReconnectRemoteDeviceHandler.class);

    private final RemoteDeviceHandler delegate;
    private final EventExecutor eventExecutor;
    private final long reconnectMillis;
    private final RemoteDeviceId id;

    ReconnectRemoteDeviceHandler(final EventExecutor eventExecutor, final RemoteDeviceId id,
            final RemoteDeviceHandler delegate, final Uint32 reconnectMillis) {
        this.eventExecutor = requireNonNull(eventExecutor);
        this.id = requireNonNull(id);
        this.delegate = requireNonNull(delegate);
        this.reconnectMillis = reconnectMillis.toJava();
    }

    @Override
    public void onDeviceDisconnected() {
        delegate.onDeviceDisconnected();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        delegate.onDeviceFailed(throwable);

        if (throwable instanceof EmptySchemaContextException) {
            LOG.warn("Reconnection is allowed! This can lead to unexpected errors at runtime.");
            LOG.warn("{} : No more sources for schema context.", id);
            eventExecutor.schedule(() -> {
                LOG.info("{} : Try to remount device.", id);


            }, reconnectMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        delegate.onNotification(domNotification);
    }

    @Override
    public void close() {
        delegate.close();
    }
}

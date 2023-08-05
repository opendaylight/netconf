/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.client.mdsal.api.DelegatedRemoteDeviceConnection;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceConnection;
import org.opendaylight.netconf.client.mdsal.spi.KeepaliveSalFacade.KeepaliveTask;

/**
 * A {@link RemoteDeviceConnection} which delegates to an another delegate and also schedules keepalives when
 * appropriate.
 */
final class KeepaliveConnection extends DelegatedRemoteDeviceConnection {
    private static final VarHandle TASK;

    static {
        try {
            TASK = MethodHandles.lookup().findVarHandle(KeepaliveConnection.class, "task", KeepaliveTask.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile KeepaliveTask task;

    KeepaliveConnection(final RemoteDeviceConnection delegate, final KeepaliveTask task) {
        super(delegate);
        this.task = requireNonNull(task);
    }

    @Override
    protected void onNotificationImpl(final DOMNotification domNotification) {
        final var localTask = task();
        if (localTask != null) {
            localTask.recordActivity();
        }
        super.onNotificationImpl(domNotification);
    }

    @Override
    protected void preCloseDelegate() {
        stopKeepalives();
    }

    /**
     * Cancel current keepalive and free it.
     */
    synchronized void stopKeepalives() {
        final var localTask = task;
        if (localTask != null) {
            localTask.disableKeepalive();
            task = null;
        }
    }

    void disableKeepalive() {
        final var localTask = task();
        if (localTask != null) {
            localTask.disableKeepalive();
        }
    }

    void enableKeepalive() {
        final var localTask = task();
        if (localTask != null) {
            localTask.enableKeepalive();
        }
    }

    private @Nullable KeepaliveTask task() {
        return (KeepaliveTask) TASK.getAcquire(this);
    }
}

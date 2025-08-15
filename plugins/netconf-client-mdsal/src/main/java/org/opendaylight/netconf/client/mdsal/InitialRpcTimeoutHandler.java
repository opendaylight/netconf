/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs.Normalized;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs.Schemaless;
import org.opendaylight.netconf.client.mdsal.api.SchemalessRpcService;
import org.opendaylight.netconf.client.mdsal.spi.KeepaliveSalFacade;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class InitialRpcTimeoutHandler implements InitialRpcHandler {
    private static final Logger LOG = LoggerFactory.getLogger(InitialRpcTimeoutHandler.class);

    private final RemoteDeviceId deviceId;
    private final long timeoutNanos;
    private final NetconfTimer timer;
    private final RemoteDeviceHandler deviceHandler;

    @Nullable
    private volatile NetconfDeviceCommunicator listener;

    public InitialRpcTimeoutHandler(final RemoteDeviceId deviceId, final RemoteDeviceHandler deviceHandler,
            final NetconfTimer timer, final long requestTimeoutMillis) {
        this.deviceHandler = requireNonNull(deviceHandler);
        this.deviceId = requireNonNull(deviceId);
        this.timer = requireNonNull(timer);
        timeoutNanos = TimeUnit.MILLISECONDS.toNanos(requestTimeoutMillis);
    }

    /**
     * Set the netconf session listener whenever ready.
     *
     * @param listener netconf session listener
     */
    public void setListener(final NetconfDeviceCommunicator listener) {
        this.listener = listener;
        if (deviceHandler instanceof KeepaliveSalFacade salFacade) {
            salFacade.setListener(listener);
        }
    }

    public RemoteDeviceHandler remoteDeviceHandler() {
        return deviceHandler;
    }

    /**
     * Wrap {@link Rpcs} with user set time-out {@code requestTimeoutMillis} and reconnect device in case of failure.
     *
     * @param service {@link Rpcs}
     * @return {@link Rpcs}
     */
    public Rpcs decorateRpcs(final Rpcs service) {
        return switch (service) {
            case Normalized normalized -> new NormalizedRpcs(normalized);
            case Schemaless schemaless -> new SchemalessRpcs(schemaless);
        };
    }

    void disconnect() {
        // Copy the volatile field to a local variable
        final var tempListener = listener;
        requireNonNull(tempListener, deviceId + ": Unable to reconnect, session listener is missing");
        tempListener.disconnect();
    }

    private <T extends DOMRpcResult> ListenableFuture<T> scheduleTimeout(
            final ListenableFuture<T> listenableFuture) {
        final var timeoutTask = new TimeoutTask<>(listenableFuture);
        final var timeout = timer.newTimeout(timeoutTask, timeoutNanos, TimeUnit.NANOSECONDS);
        Futures.addCallback(listenableFuture, timeoutTask, MoreExecutors.directExecutor());
        listenableFuture.addListener(timeout::cancel, MoreExecutors.directExecutor());
        return timeoutTask.userFuture;
    }

    private final class NormalizedRpcs implements Normalized {
        private final Normalized delegate;

        NormalizedRpcs(final Normalized delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
            return scheduleTimeout(delegate.invokeNetconf(type, input));
        }

        @Override
        public DOMRpcService domRpcService() {
            return delegate.domRpcService();
        }
    }

    private final class SchemalessRpcs implements Schemaless {
        private final Schemaless delegate;

        SchemalessRpcs(final Schemaless delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
            return scheduleTimeout(delegate.invokeNetconf(type, input));
        }

        @Override
        public SchemalessRpcService schemalessRpcService() {
            return delegate.schemalessRpcService();
        }
    }


    private final class TimeoutTask<V> implements TimerTask, FutureCallback<V> {
        private final ListenableFuture<V> future;
        private final SettableFuture<V> userFuture = SettableFuture.create();

        TimeoutTask(final ListenableFuture<V> future) {
            this.future = requireNonNull(future);
        }

        @Override
        public void run(final Timeout timeout) {
            future.cancel(true);
        }

        @Override
        public void onSuccess(final V result) {
            userFuture.set(result);
        }

        @Override
        public void onFailure(final Throwable throwable) {
            if (throwable instanceof CancellationException) {
                LOG.warn("RPC timed out. Reconnecting netconf session");
            } else {
                LOG.warn("RPC failed. Reconnecting netconf session", throwable);
            }
            userFuture.setException(throwable);
            disconnect();
        }
    }
}

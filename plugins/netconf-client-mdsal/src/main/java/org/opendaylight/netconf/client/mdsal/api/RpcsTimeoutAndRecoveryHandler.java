/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs.Normalized;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs.Schemaless;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle user-specified requestTimeoutMillis and recovery for RPCs. If the RPC request throws an exception during
 * execution, the device is disconnected.
 */
@NonNullByDefault
public final class RpcsTimeoutAndRecoveryHandler implements RpcsDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(RpcsTimeoutAndRecoveryHandler.class);

    private final RemoteDeviceId deviceId;
    private final NetconfTimer timer;
    private final long timeoutNanos;

    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
        justification = "Field is @Nullable and is intended to be initialized later, not in the constructor.")
    private @Nullable NetconfDeviceCommunicator listener;

    public RpcsTimeoutAndRecoveryHandler(final RemoteDeviceId deviceId, final NetconfTimer timer,
            final long requestTimeoutMillis) {
        this.deviceId = requireNonNull(deviceId);
        this.timer = requireNonNull(timer);
        timeoutNanos = TimeUnit.MILLISECONDS.toNanos(requestTimeoutMillis);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wrap {@link Rpcs} with user set time-out {@code requestTimeoutMillis} and reconnect device in case of failure.
     *
     * @param service {@link Rpcs}
     * @return {@link Rpcs}
     */
    @Override
    public Rpcs decorateRpcs(final Rpcs service) {
        return switch (service) {
            case Normalized normalized -> new NormalizedTimeoutRpcs(normalized);
            case Schemaless schemaless -> new SchemalessTimeoutRpcs(schemaless);
        };
    }

    /**
     * Set the netconf session listener whenever ready.
     *
     * @param listener netconf session listener
     */
    public void setListener(final NetconfDeviceCommunicator listener) {
        this.listener = listener;
    }

    private <T> ListenableFuture<T> scheduleTimeout(final ListenableFuture<T> invokedRpc) {
        // Create the task responsible for cancelling provided invokedRpc and resolving the user future.
        // It also handles device disconnection upon RPC failure (timeout or exception).
        final var timeoutTask = new RequestTimeoutTask<>(invokedRpc);
        Futures.addCallback(invokedRpc, timeoutTask, MoreExecutors.directExecutor());

        // Schedule a timeout using the timer. This will throw CancellationException in RequestTimeoutTask future
        // if the timeout expires.
        final var timeout = timer.newTimeout(timeoutTask, timeoutNanos, TimeUnit.NANOSECONDS);
        invokedRpc.addListener(timeout::cancel, MoreExecutors.directExecutor());

        return timeoutTask.userFuture;
    }

    /**
     * Proxy for {@link Normalized} which schedule {@link RequestTimeoutTask} to each RPC invocation.
     */
    private final class NormalizedTimeoutRpcs implements Normalized {
        private final Normalized delegate;
        private final DOMRpcService rpcService;

        NormalizedTimeoutRpcs(final Normalized delegate) {
            this.delegate = requireNonNull(delegate);
            this.rpcService = new DOMRpcTimeoutService(delegate.domRpcService());
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
            return scheduleTimeout(delegate.invokeNetconf(type, input));
        }

        @Override
        public DOMRpcService domRpcService() {
            return rpcService;
        }
    }

    /**
     * Proxy for {@link DOMRpcService} which schedule {@link RequestTimeoutTask} to each RPC invocation.
     */
    private final class DOMRpcTimeoutService implements DOMRpcService {
        private final DOMRpcService delegate;

        DOMRpcTimeoutService(final DOMRpcService delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeRpc(final QName type, final ContainerNode input) {
            return scheduleTimeout(delegate.invokeRpc(type, input));
        }

        @Override
        public Registration registerRpcListener(final DOMRpcAvailabilityListener rpcListener) {
            // There is no real communication with the device (yet), hence no recordActivity() or anything
            return delegate.registerRpcListener(rpcListener);
        }
    }

    /**
     * Proxy for {@link Schemaless} which schedule {@link RequestTimeoutTask} to each RPC invocation.
     */
    private final class SchemalessTimeoutRpcs implements Schemaless {
        private final Schemaless delegate;
        private final SchemalessTimeoutRpcService timeoutRpcService;

        SchemalessTimeoutRpcs(final Schemaless delegate) {
            this.delegate = requireNonNull(delegate);
            this.timeoutRpcService = new SchemalessTimeoutRpcService(delegate.schemalessRpcService());
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
            return scheduleTimeout(delegate.invokeNetconf(type, input));
        }

        @Override
        public SchemalessRpcService schemalessRpcService() {
            return timeoutRpcService;
        }
    }

    /**
     * Proxy for {@link SchemalessRpcService} which schedule {@link RequestTimeoutTask} to each RPC invocation.
     */
    private final class SchemalessTimeoutRpcService implements SchemalessRpcService {
        private final SchemalessRpcService delegate;

        SchemalessTimeoutRpcService(final SchemalessRpcService delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public ListenableFuture<? extends DOMSource> invokeRpc(final QName type, final DOMSource payload) {
            return scheduleTimeout(delegate.invokeRpc(type, payload));
        }
    }

    /**
     * Request timeout task is called once the requestTimeoutMillis is reached. At that moment, if the request is not
     * yet finished, we cancel it. If the RPC fails for any reason, the device is disconnected.
     *
     * @param <V> the type of the result returned by the RPC
     */
    private final class RequestTimeoutTask<V> implements TimerTask, FutureCallback<V> {
        private final ListenableFuture<V> future;
        private final SettableFuture<V> userFuture = SettableFuture.create();

        RequestTimeoutTask(final ListenableFuture<V> future) {
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
            // User/Application RPC failed (The RPC did not reach the remote device or it has timeed out)
            if (throwable instanceof CancellationException) {
                LOG.warn("{}: RPC timed out. Reconnecting netconf session", deviceId);
            } else {
                LOG.warn("{}: RPC failed. Reconnecting netconf session", deviceId, throwable);
            }
            userFuture.setException(throwable);
            requireNonNull(listener, deviceId + ": Unable to reconnect, session listener is missing");
            // There is no point in keeping this session. Reconnect.
            listener.disconnect();
        }
    }
}

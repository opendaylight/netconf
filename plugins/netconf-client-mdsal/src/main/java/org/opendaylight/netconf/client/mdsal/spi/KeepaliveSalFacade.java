/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps.getSourceNode;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import javax.xml.transform.dom.DOMSource;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.SchemalessRpcService;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SalFacade proxy that invokes keepalive RPCs to prevent session shutdown from remote device
 * and to detect incorrect session drops (netconf session is inactive, but TCP/SSH connection is still present).
 * The keepalive RPC is a get-config with empty filter.
 */
public final class KeepaliveSalFacade implements RemoteDeviceHandler {
    private static final Logger LOG = LoggerFactory.getLogger(KeepaliveSalFacade.class);

    // 2 minutes keepalive delay by default
    private static final long DEFAULT_DELAY = TimeUnit.MINUTES.toSeconds(2);

    // 1 minute transaction timeout by default
    private static final long DEFAULT_TRANSACTION_TIMEOUT_MILLI = TimeUnit.MILLISECONDS.toMillis(60000);

    private final RemoteDeviceHandler deviceHandler;
    private final RemoteDeviceId deviceId;
    private final NetconfTimer timer;
    private final long keepaliveDelaySeconds;
    private final long timeoutNanos;
    private final long delayNanos;

    private volatile NetconfDeviceCommunicator listener;
    private volatile KeepaliveTask task;

    public KeepaliveSalFacade(final RemoteDeviceId deviceId, final RemoteDeviceHandler deviceHandler,
            final NetconfTimer timer, final long keepaliveDelaySeconds, final long requestTimeoutMillis) {
        this.deviceId = requireNonNull(deviceId);
        this.deviceHandler = requireNonNull(deviceHandler);
        this.timer = requireNonNull(timer);
        this.keepaliveDelaySeconds = keepaliveDelaySeconds;
        delayNanos = TimeUnit.SECONDS.toNanos(keepaliveDelaySeconds);
        timeoutNanos = TimeUnit.MILLISECONDS.toNanos(requestTimeoutMillis);
    }

    public KeepaliveSalFacade(final RemoteDeviceId deviceId, final RemoteDeviceHandler deviceHandler,
            final NetconfTimer timer) {
        this(deviceId, deviceHandler, timer, DEFAULT_DELAY, DEFAULT_TRANSACTION_TIMEOUT_MILLI);
    }

    /**
     * Set the netconf session listener whenever ready.
     *
     * @param listener netconf session listener
     */
    public void setListener(final NetconfDeviceCommunicator listener) {
        this.listener = listener;
    }

    /**
     * Cancel current keepalive and free it.
     */
    private synchronized void stopKeepalives() {
        final var localTask = task;
        if (localTask != null) {
            localTask.disableKeepalive();
            task = null;
        }
    }

    private void disableKeepalive() {
        final var localTask = task;
        if (localTask != null) {
            localTask.disableKeepalive();
        }
    }

    private void enableKeepalive() {
        final var localTask = task;
        if (localTask != null) {
            localTask.enableKeepalive();
        }
    }

    private void disconnect() {
        checkState(listener != null, "%s: Unable to reconnect, session listener is missing", deviceId);
        stopKeepalives();
        LOG.info("{}: Reconnecting inactive netconf session", deviceId);
        listener.disconnect();
    }

    @Override
    public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        final var devRpc = services.rpcs();
        task = new KeepaliveTask(devRpc);

        final Rpcs keepaliveRpcs;
        if (devRpc instanceof Rpcs.Normalized normalized) {
            keepaliveRpcs = new NormalizedKeepaliveRpcs(normalized);
        } else if (devRpc instanceof Rpcs.Schemaless schemaless) {
            keepaliveRpcs = new SchemalessKeepaliveRpcs(schemaless);
        } else {
            throw new IllegalStateException("Unhandled " + devRpc);
        }

        deviceHandler.onDeviceConnected(deviceSchema, sessionPreferences, new RemoteDeviceServices(keepaliveRpcs,
            // FIXME: wrap with keepalive
            services.actions()));

        // We have performed a callback, which might have termined keepalives
        final var localTask = task;
        if (localTask != null) {
            LOG.debug("{}: Netconf session initiated, starting keepalives", deviceId);
            LOG.trace("{}: Scheduling keepalives every {}s", deviceId, keepaliveDelaySeconds);
            // schedule first KeepaliveTask and do not decrement suppressedCounter at the same time
            localTask.reschedule();
        }
    }

    @Override
    public void onDeviceDisconnected() {
        stopKeepalives();
        deviceHandler.onDeviceDisconnected();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        stopKeepalives();
        deviceHandler.onDeviceFailed(throwable);
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        final var localTask = task;
        if (localTask != null) {
            localTask.recordActivity();
        }
        deviceHandler.onNotification(domNotification);
    }

    @Override
    public void close() {
        stopKeepalives();
        deviceHandler.close();
    }

    private <T> @NonNull ListenableFuture<T> scheduleTimeout(final ListenableFuture<T> invokeFuture) {
        final var timeout = new RequestTimeoutTask<>(invokeFuture);
        scheduleTimeout(invokeFuture, timeout);
        return timeout.userFuture;
    }

    private void scheduleTimeout(final ListenableFuture<?> future, final TimeoutTask timeoutTask) {
        final var timeout = timer.newTimeout(timeoutTask, timeoutNanos, TimeUnit.NANOSECONDS);
        future.addListener(() -> timeout.cancel(), MoreExecutors.directExecutor());
    }

    /**
     * Invoke keepalive RPC and check the response. In case of any received response the keepalive
     * is considered successful and schedules next keepalive with a fixed delay. If the response is unsuccessful (no
     * response received, or the rcp could not even be sent) immediate reconnect is triggered as netconf session
     * is considered inactive/failed.
     */
    @VisibleForTesting
    final class KeepaliveTask implements TimerTask, FutureCallback<DOMRpcResult> {
        // Keepalive RPC static resources
        static final @NonNull ContainerNode KEEPALIVE_PAYLOAD = NetconfMessageTransformUtil.wrap(
            NETCONF_GET_CONFIG_NODEID, getSourceNode(NETCONF_RUNNING_NODEID), NetconfMessageTransformUtil.EMPTY_FILTER);

        private final Rpcs devRpc;

        @GuardedBy("this")
        private boolean suppressed = false;

        @GuardedBy("this")
        private int suppressedCounter = 0;

        private volatile long lastActivity;

        KeepaliveTask(final Rpcs devRpc) {
            this.devRpc = requireNonNull(devRpc);
        }

        @Override
        public void run(final Timeout timeout) {
            final long local = lastActivity;
            final long now = System.nanoTime();
            final long inFutureNanos = local + delayNanos - now;
            if (inFutureNanos > 0) {
                reschedule(inFutureNanos);
            } else {
                sendKeepalive(now);
            }
        }

        void recordActivity() {
            lastActivity = System.nanoTime();
        }

        synchronized void disableKeepalive() {
            // unsuppressed -> suppressed
            suppressed = true;
            suppressedCounter++;
        }

        synchronized void enableKeepalive() {
            recordActivity();
            suppressedCounter--;
            if (suppressedCounter > 0) {
                LOG.debug("{}: Skipping to enable keepalive while expecting {} RPC reply", deviceId, suppressedCounter);
                return;
            }
            if (suppressed) {
                // suppressed -> unsuppressed
                suppressed = false;
            } else {
                // unscheduled -> unsuppressed
                reschedule();
            }
        }

        private void sendKeepalive(final long now) {
            synchronized (this) {
                if (suppressed) {
                    LOG.debug("{}: Skipping keepalive while disabled", deviceId);
                    // suppressed -> unscheduled
                    suppressed = false;
                    return;
                }
            }

            LOG.trace("{}: Invoking keepalive RPC", deviceId);
            final var deviceFuture = devRpc.invokeNetconf(GetConfig.QNAME, KEEPALIVE_PAYLOAD);
            lastActivity = now;

            scheduleTimeout(deviceFuture, new TimeoutTask(deviceFuture));
            Futures.addCallback(deviceFuture, this, MoreExecutors.directExecutor());
        }

        @Override
        public void onSuccess(final DOMRpcResult result) {
            // No matter what response we got, rpc-reply or rpc-error,
            // we got it from device so the netconf session is OK
            if (result == null) {
                LOG.warn("{} Keepalive RPC returned null with response. Reconnecting netconf session", deviceId);
                disconnect();
                return;
            }

            if (result.value() != null) {
                reschedule();
            } else {
                final var errors = result.errors();
                if (!errors.isEmpty()) {
                    LOG.warn("{}: Keepalive RPC failed with error: {}", deviceId, errors);
                    reschedule();
                } else {
                    LOG.warn("{} Keepalive RPC returned null with response. Reconnecting netconf session", deviceId);
                    disconnect();
                }
            }
        }

        @Override
        public void onFailure(final Throwable throwable) {
            if (throwable instanceof CancellationException) {
                LOG.warn("{}: Keepalive RPC timed out. Reconnecting netconf session.", deviceId);
            } else {
                LOG.warn("{}: Keepalive RPC failed. Reconnecting netconf session.", deviceId, throwable);
            }
            disconnect();
        }

        private void reschedule() {
            reschedule(delayNanos);
        }

        private void reschedule(final long delay) {
            timer.newTimeout(this, delay, TimeUnit.NANOSECONDS);
        }
    }

    private static class TimeoutTask implements TimerTask {
        private final ListenableFuture<?> future;

        TimeoutTask(final ListenableFuture<?> future) {
            this.future = requireNonNull(future);
        }

        @Override
        public final void run(final Timeout timeout) {
            future.cancel(true);
        }
    }

    /*
     * Request timeout task is called once the requestTimeoutMillis is reached. At that moment, if the request is not
     * yet finished, we cancel it.
     */
    private final class RequestTimeoutTask<V> extends TimeoutTask implements FutureCallback<V> {
        private final @NonNull SettableFuture<V> userFuture = SettableFuture.create();

        RequestTimeoutTask(final ListenableFuture<V> rpcResultFuture) {
            super(rpcResultFuture);
            // Note: this will also wire run() to onFailure()
            Futures.addCallback(rpcResultFuture, this, MoreExecutors.directExecutor());
        }

        @Override
        public void onSuccess(final V result) {
            // No matter what response we got,
            // rpc-reply or rpc-error, we got it from device so the netconf session is OK.
            userFuture.set(result);
            enableKeepalive();
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
            // There is no point in keeping this session. Reconnect.
            disconnect();
        }
    }

    /**
     * Proxy for {@link Rpcs} which attaches a reset-keepalive-task and schedule request-timeout-task to each RPC
     * invocation. Version for {@link Rpcs.Normalized}.
     */
    private final class NormalizedKeepaliveRpcs implements Rpcs.Normalized {
        private final @NonNull KeepaliveDOMRpcService domRpcService;
        private final Rpcs.Normalized delegate;

        NormalizedKeepaliveRpcs(final Rpcs.Normalized delegate) {
            this.delegate = requireNonNull(delegate);
            domRpcService = new KeepaliveDOMRpcService(delegate.domRpcService());
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
            // FIXME: what happens if we disable keepalive and then invokeRpc() throws?
            disableKeepalive();
            return scheduleTimeout(delegate.invokeNetconf(type, input));
        }

        @Override
        public DOMRpcService domRpcService() {
            return domRpcService;
        }
    }

    private final class KeepaliveDOMRpcService implements DOMRpcService {
        private final @NonNull DOMRpcService delegate;

        KeepaliveDOMRpcService(final DOMRpcService delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeRpc(final QName type, final ContainerNode input) {
            // FIXME: what happens if we disable keepalive and then invokeRpc() throws?
            disableKeepalive();
            return scheduleTimeout(delegate.invokeRpc(type, input));
        }

        @Override
        public Registration registerRpcListener(final DOMRpcAvailabilityListener rpcListener) {
            // There is no real communication with the device (yet), hence no recordActivity() or anything
            return delegate.registerRpcListener(rpcListener);
        }
    }

    /**
     * Proxy for {@link Rpcs} which attaches a reset-keepalive-task and schedule request-timeout-task to each RPC
     * invocation. Version for {@link Rpcs.Schemaless}.
     */
    private final class SchemalessKeepaliveRpcs implements Rpcs.Schemaless {
        private final @NonNull KeepaliveSchemalessRpcService schemalessRpcService;
        private final Rpcs.Schemaless delegate;

        SchemalessKeepaliveRpcs(final Rpcs.Schemaless delegate) {
            this.delegate = requireNonNull(delegate);
            schemalessRpcService = new KeepaliveSchemalessRpcService(delegate.schemalessRpcService());
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type, final ContainerNode input) {
            // FIXME: what happens if we disable keepalive and then invokeRpc() throws?
            disableKeepalive();
            return scheduleTimeout(delegate.invokeNetconf(type, input));
        }

        @Override
        public SchemalessRpcService schemalessRpcService() {
            return schemalessRpcService;
        }
    }

    private final class KeepaliveSchemalessRpcService implements SchemalessRpcService {
        private final SchemalessRpcService delegate;

        KeepaliveSchemalessRpcService(final SchemalessRpcService delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public ListenableFuture<? extends DOMSource> invokeRpc(final QName type, final DOMSource payload) {
            // FIXME: what happens if we disable keepalive and then invokeRpc() throws?
            disableKeepalive();
            return scheduleTimeout(delegate.invokeRpc(type, payload));
        }
    }
}

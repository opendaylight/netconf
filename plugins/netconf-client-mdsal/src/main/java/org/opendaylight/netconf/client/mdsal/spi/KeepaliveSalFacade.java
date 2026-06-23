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
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.xml.transform.dom.DOMSource;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcsTimeoutAndRecoveryHandler.NormalizedTimeoutRpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcsTimeoutAndRecoveryHandler.SchemalessTimeoutRpcs;
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

    private final RemoteDeviceHandler deviceHandler;
    private final RemoteDeviceId deviceId;
    private final NetconfTimer timer;
    private final long keepaliveDelaySeconds;
    private final long delayNanos;

    private volatile NetconfDeviceCommunicator listener;
    private volatile KeepaliveTask task;

    public KeepaliveSalFacade(final RemoteDeviceId deviceId, final RemoteDeviceHandler deviceHandler,
            final NetconfTimer timer, final long keepaliveDelaySeconds) {
        this.deviceId = requireNonNull(deviceId);
        this.deviceHandler = requireNonNull(deviceHandler);
        this.timer = requireNonNull(timer);
        this.keepaliveDelaySeconds = keepaliveDelaySeconds;
        delayNanos = TimeUnit.SECONDS.toNanos(keepaliveDelaySeconds);
    }

    public KeepaliveSalFacade(final RemoteDeviceId deviceId, final RemoteDeviceHandler deviceHandler,
            final NetconfTimer timer) {
        this(deviceId, deviceHandler, timer, DEFAULT_DELAY);
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
    private void stopKeepalive() {
        final var localTask = task;
        if (localTask != null) {
            localTask.stopKeepalive();
            task = null;
        }
    }

    private void recordActivity() {
        final var localTask = task;
        if (localTask != null) {
            localTask.recordActivity();
        }
    }

    private void disconnect() {
        checkState(listener != null, "%s: Unable to reconnect, session listener is missing", deviceId);
        stopKeepalive();
        LOG.info("{}: Reconnecting inactive netconf session", deviceId);
        listener.disconnect();
    }

    @Override
    public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services,
            final NegotiatedSshAlg negotiatedSshAlg) {
        final var devRpc = services.rpcs();
        task = new KeepaliveTask(devRpc);

        final Rpcs keepaliveRpcs;
        if (devRpc instanceof NormalizedTimeoutRpcs normalized) {
            keepaliveRpcs = new NormalizedKeepaliveRpcs(normalized);
        } else if (devRpc instanceof SchemalessTimeoutRpcs schemaless) {
            keepaliveRpcs = new SchemalessKeepaliveRpcs(schemaless);
        } else {
            throw new IllegalStateException("Unhandled " + devRpc);
        }

        deviceHandler.onDeviceConnected(deviceSchema, sessionPreferences, new RemoteDeviceServices(keepaliveRpcs,
            // FIXME: wrap with keepalive
            services.actions()), negotiatedSshAlg);

        // We have performed a callback, which might have terminated keepalives
        final var localTask = task;
        if (localTask != null) {
            LOG.debug("{}: Netconf session initiated, starting keepalives", deviceId);
            LOG.trace("{}: Scheduling keepalives every {}s", deviceId, keepaliveDelaySeconds);
            // Schedule initial KeepaliveTask.
            localTask.reschedule();
        }
    }

    @Override
    public void onDeviceDisconnected() {
        stopKeepalive();
        deviceHandler.onDeviceDisconnected();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        stopKeepalive();
        deviceHandler.onDeviceFailed(throwable);
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        recordActivity();
        deviceHandler.onNotification(domNotification);
    }

    @Override
    public void close() {
        stopKeepalive();
        deviceHandler.close();
    }

    /**
     * Tracks a user RPC as in-flight so keepalives are deferred until it completes.
     *
     * <p>Synchronous failures, including {@link Error}, must clear in-flight tracking before propagating.
     * Catching both types ensures synchronous delegate failure cannot leave in-flight tracking stuck.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private <T> ListenableFuture<T> trackRpcInvocation(final Supplier<ListenableFuture<T>> invocation) {
        final var localTask = task;
        final var tracked = localTask != null && localTask.requestStarted();

        final ListenableFuture<T> invokedRpc;
        try {
            invokedRpc = invocation.get();
        } catch (RuntimeException | Error e) {
            if (tracked) {
                localTask.requestFinished();
            }
            throw e;
        }

        if (tracked) {
            Futures.addCallback(invokedRpc, new FutureCallback<>() {
                @Override
                public void onSuccess(final T result) {
                    localTask.requestFinished();
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    localTask.requestFinished();
                }
            }, MoreExecutors.directExecutor());
        }
        return invokedRpc;
    }

    /**
     * Invoke keepalive RPC and check the response. In case of any received response the keepalive
     * is considered successful and the next keepalive is scheduled based on the last observed activity.
     * If the response is unsuccessful (no response received, or the rpc could not even be sent), an
     * immediate reconnect is triggered as the netconf session is considered inactive/failed.
     */
    @VisibleForTesting
    final class KeepaliveTask implements TimerTask, FutureCallback<DOMRpcResult> {
        // Keepalive RPC static resources
        static final @NonNull ContainerNode KEEPALIVE_PAYLOAD = NetconfMessageTransformUtil.wrap(
            NETCONF_GET_CONFIG_NODEID, getSourceNode(NETCONF_RUNNING_NODEID), NetconfMessageTransformUtil.EMPTY_FILTER);

        private final Rpcs devRpc;

        @GuardedBy("this")
        private long lastActivity;
        @GuardedBy("this")
        private int inFlightRequests;

        private volatile boolean disabled;

        KeepaliveTask(final Rpcs devRpc) {
            this.devRpc = requireNonNull(devRpc);
        }

        @Override
        public void run(final Timeout timeout) {
            final long delay;
            synchronized (this) {
                // Stop here if a concurrent stopKeepalive() has fired.
                if (disabled) {
                    LOG.debug("{}: Keepalive already stopped - ignoring timeout", deviceId);
                    return;
                }
                if (inFlightRequests > 0) {
                    delay = delayNanos;
                } else {
                    final long now = System.nanoTime();
                    final long inFutureNanos = lastActivity + delayNanos - now;
                    delay = inFutureNanos > 0 ? inFutureNanos : 0;
                }
            }
            if (delay > 0) {
                reschedule(delay);
            } else {
                sendKeepalive();
            }
        }

        synchronized void recordActivity() {
            lastActivity = System.nanoTime();
        }

        synchronized boolean requestStarted() {
            if (disabled) {
                return false;
            }
            inFlightRequests++;
            return true;
        }

        synchronized void requestFinished() {
            checkState(inFlightRequests > 0, "%s: User RPC finished without matching start", deviceId);
            inFlightRequests--;
            lastActivity = System.nanoTime();
        }

        void stopKeepalive() {
            disabled = true;
        }

        // Treat any synchronous keepalive RPC failure as a session failure requiring reconnect.
        @SuppressWarnings("checkstyle:IllegalCatch")
        private void sendKeepalive() {
            final boolean reschedule;
            synchronized (this) {
                // Re-check because run() released the lock before invoking this method.
                if (disabled) {
                    return;
                }
                reschedule = inFlightRequests > 0;
            }
            if (reschedule) {
                reschedule();
                return;
            }

            LOG.trace("{}: Invoking keepalive RPC", deviceId);
            final ListenableFuture<? extends DOMRpcResult> deviceFuture;
            try {
                deviceFuture = devRpc.invokeNetconf(GetConfig.QNAME, KEEPALIVE_PAYLOAD);
            } catch (RuntimeException e) {
                LOG.warn("{}: Keepalive RPC could not be sent. Reconnecting netconf session.", deviceId, e);
                disconnect();
                return;
            }

            Futures.addCallback(deviceFuture, this, MoreExecutors.directExecutor());
        }

        @Override
        public void onSuccess(final DOMRpcResult result) {
            if (disabled) {
                return;
            }
            // No matter what response we got, rpc-reply or rpc-error,
            // we got it from device so the netconf session is OK
            if (result == null) {
                LOG.warn("{}: Keepalive RPC returned null with response. Reconnecting netconf session", deviceId);
                disconnect();
                return;
            }

            if (result.value() != null) {
                recordActivity();
                reschedule();
            } else {
                final var errors = result.errors();
                if (!errors.isEmpty()) {
                    LOG.warn("{}: Keepalive RPC failed with error: {}", deviceId, errors);
                    recordActivity();
                    reschedule();
                } else {
                    LOG.warn("{}: Keepalive RPC returned empty response. Reconnecting netconf session", deviceId);
                    disconnect();
                }
            }
        }

        @Override
        public void onFailure(final Throwable throwable) {
            if (disabled) {
                return;
            }
            if (throwable instanceof CancellationException) {
                LOG.warn("{}: Keepalive RPC timed out.", deviceId);
            } else {
                LOG.warn("{}: Keepalive RPC failed.", deviceId, throwable);
            }
        }

        private void reschedule() {
            reschedule(delayNanos);
        }

        private void reschedule(final long delay) {
            if (disabled) {
                return;
            }
            timer.newTimeout(this, delay, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Proxy for {@link Rpcs} which attaches a reset-keepalive-task to each RPC invocation.
     * Version for {@link Rpcs.Normalized}.
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
            return trackRpcInvocation(() -> delegate.invokeNetconf(type, input));
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
            return trackRpcInvocation(() -> delegate.invokeRpc(type, input));
        }

        @Override
        public Registration registerRpcListener(final DOMRpcAvailabilityListener rpcListener) {
            // There is no real communication with the device (yet), hence no recordActivity() or anything
            return delegate.registerRpcListener(rpcListener);
        }
    }

    /**
     * Proxy for {@link Rpcs} which attaches a reset-keepalive-task to each RPC invocation.
     * Version for {@link Rpcs.Schemaless}.
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
            return trackRpcInvocation(() -> delegate.invokeNetconf(type, input));
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
            return trackRpcInvocation(() -> delegate.invokeRpc(type, payload));
        }
    }
}

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps.getSourceNode;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SalFacade proxy that invokes keepalive RPCs to prevent session shutdown from remote device
 * and to detect incorrect session drops (netconf session is inactive, but TCP/SSH connection is still present).
 * The keepalive RPC is a get-config with empty filter.
 */
public final class KeepaliveSalFacade implements RemoteDeviceHandler<NetconfSessionPreferences> {
    private static final Logger LOG = LoggerFactory.getLogger(KeepaliveSalFacade.class);

    // 2 minutes keepalive delay by default
    private static final long DEFAULT_DELAY = TimeUnit.MINUTES.toSeconds(2);

    // 1 minute transaction timeout by default
    private static final long DEFAULT_TRANSACTION_TIMEOUT_MILLI = TimeUnit.MILLISECONDS.toMillis(60000);

    private final KeepaliveTask keepaliveTask = new KeepaliveTask();
    private final RemoteDeviceHandler<NetconfSessionPreferences> salFacade;
    private final ScheduledExecutorService executor;

    private final long keepaliveDelaySeconds;
    private final long timeoutNanos;
    private final long delayNanos;

    private final RemoteDeviceId id;

    private volatile NetconfDeviceCommunicator listener;
    private volatile DOMRpcService currentDeviceRpc;

    public KeepaliveSalFacade(final RemoteDeviceId id, final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
                              final ScheduledExecutorService executor, final long keepaliveDelaySeconds,
                              final long requestTimeoutMillis) {
        this.id = id;
        this.salFacade = salFacade;
        this.executor = requireNonNull(executor);
        this.keepaliveDelaySeconds = keepaliveDelaySeconds;
        delayNanos = TimeUnit.SECONDS.toNanos(keepaliveDelaySeconds);
        timeoutNanos = TimeUnit.MILLISECONDS.toNanos(requestTimeoutMillis);
    }

    public KeepaliveSalFacade(final RemoteDeviceId id, final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
                              final ScheduledExecutorService executor) {
        this(id, salFacade, executor, DEFAULT_DELAY, DEFAULT_TRANSACTION_TIMEOUT_MILLI);
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
     * Cancel current keepalive and also reset current deviceRpc.
     */
    private synchronized void stopKeepalives() {
        keepaliveTask.disableKeepalive();
        currentDeviceRpc = null;
    }

    void reconnect() {
        checkState(listener != null, "%s: Unable to reconnect, session listener is missing", id);
        stopKeepalives();
        LOG.info("{}: Reconnecting inactive netconf session", id);
        listener.disconnect();
    }

    @Override
    public void onDeviceConnected(final MountPointContext remoteSchemaContext,
                          final NetconfSessionPreferences netconfSessionPreferences, final DOMRpcService deviceRpc) {
        onDeviceConnected(remoteSchemaContext, netconfSessionPreferences, deviceRpc, null);
    }

    @Override
    public void onDeviceConnected(final MountPointContext remoteSchemaContext,
            final NetconfSessionPreferences netconfSessionPreferences, final DOMRpcService deviceRpc,
            final DOMActionService deviceAction) {
        this.currentDeviceRpc = requireNonNull(deviceRpc);
        salFacade.onDeviceConnected(remoteSchemaContext, netconfSessionPreferences,
            new KeepaliveDOMRpcService(deviceRpc), deviceAction);

        LOG.debug("{}: Netconf session initiated, starting keepalives", id);
        LOG.trace("{}: Scheduling keepalives every {}s", id, keepaliveDelaySeconds);
        keepaliveTask.enableKeepalive();
    }

    @Override
    public void onDeviceDisconnected() {
        stopKeepalives();
        salFacade.onDeviceDisconnected();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        stopKeepalives();
        salFacade.onDeviceFailed(throwable);
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        keepaliveTask.recordActivity();
        salFacade.onNotification(domNotification);
    }

    @Override
    public void close() {
        stopKeepalives();
        salFacade.close();
    }

    // Keepalive RPC static resources
    private static final ContainerNode KEEPALIVE_PAYLOAD = NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID,
            getSourceNode(NETCONF_RUNNING_QNAME), NetconfMessageTransformUtil.EMPTY_FILTER);

    /**
     * Invoke keepalive RPC and check the response. In case of any received response the keepalive
     * is considered successful and schedules next keepalive with a fixed delay. If the response is unsuccessful (no
     * response received, or the rcp could not even be sent) immediate reconnect is triggered as netconf session
     * is considered inactive/failed.
     */
    private final class KeepaliveTask implements Runnable, FutureCallback<DOMRpcResult> {
        private volatile long lastActivity;
        @GuardedBy("this")
        private boolean suppressed;

        KeepaliveTask() {
            suppressed = false;
        }

        @Override
        public void run() {
            final long local = lastActivity;
            final long now = System.nanoTime();
            final long inFutureNanos = local + delayNanos - now;
            if (inFutureNanos > 0) {
                reschedule(inFutureNanos);
            } else {
                sendKeepalive(now);
            }
        }

        private void recordActivity() {
            lastActivity = System.nanoTime();
        }

        synchronized void disableKeepalive() {
            // unsuppressed -> suppressed
            suppressed = true;
        }

        synchronized void enableKeepalive() {
            recordActivity();
            if (!suppressed) {
                // unscheduled -> unsuppressed
                reschedule();
            } else {
                // suppressed -> unsuppressed
                suppressed = false;
            }
        }

        private synchronized void sendKeepalive(final long now) {
            if (suppressed) {
                // suppressed -> unscheduled
                suppressed = false;
                return;
            }

            final DOMRpcService deviceRpc = currentDeviceRpc;
            if (deviceRpc == null) {
                // deviceRpc is null, which means we hit the reconnect window and attempted to send keepalive while
                // we were reconnecting. Next keepalive will be scheduled after reconnect so no action necessary here.
                LOG.debug("{}: Skipping keepalive while reconnecting", id);
                return;
            }

            LOG.trace("{}: Invoking keepalive RPC", id);
            final ListenableFuture<? extends DOMRpcResult> deviceFuture =
                currentDeviceRpc.invokeRpc(NETCONF_GET_CONFIG_QNAME, KEEPALIVE_PAYLOAD);

            lastActivity = now;
            Futures.addCallback(deviceFuture, this, MoreExecutors.directExecutor());
        }

        @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
                justification = "Unrecognised NullableDecl")
        @Override
        public void onSuccess(final DOMRpcResult result) {
            // No matter what response we got, rpc-reply or rpc-error,
            // we got it from device so the netconf session is OK
            if (result == null) {
                LOG.warn("{} Keepalive RPC returned null with response. Reconnecting netconf session", id);
                reconnect();
                return;
            }

            if (result.getResult() != null) {
                reschedule();
            } else {
                final Collection<?> errors = result.getErrors();
                if (!errors.isEmpty()) {
                    LOG.warn("{}: Keepalive RPC failed with error: {}", id, errors);
                    reschedule();
                } else {
                    LOG.warn("{} Keepalive RPC returned null with response. Reconnecting netconf session", id);
                    reconnect();
                }
            }
        }

        @Override
        public void onFailure(final Throwable throwable) {
            LOG.warn("{}: Keepalive RPC failed. Reconnecting netconf session.", id, throwable);
            reconnect();
        }

        private void reschedule() {
            reschedule(delayNanos);
        }

        private void reschedule(final long delay) {
            executor.schedule(this, delay, TimeUnit.NANOSECONDS);
        }
    }

    /*
     * Request timeout task is called once the requestTimeoutMillis is reached. At that moment, if the request is not
     * yet finished, we cancel it.
     */
    private final class RequestTimeoutTask implements FutureCallback<DOMRpcResult>, Runnable {
        private final @NonNull SettableFuture<DOMRpcResult> userFuture = SettableFuture.create();
        private final @NonNull ListenableFuture<? extends DOMRpcResult> deviceFuture;

        RequestTimeoutTask(final ListenableFuture<? extends DOMRpcResult> rpcResultFuture) {
            this.deviceFuture = requireNonNull(rpcResultFuture);
            Futures.addCallback(deviceFuture, this, MoreExecutors.directExecutor());
        }

        @Override
        public void run() {
            deviceFuture.cancel(true);
            userFuture.cancel(false);
            keepaliveTask.enableKeepalive();
        }

        @Override
        public void onSuccess(final DOMRpcResult result) {
            // No matter what response we got,
            // rpc-reply or rpc-error, we got it from device so the netconf session is OK.
            userFuture.set(result);
            keepaliveTask.enableKeepalive();
        }

        @Override
        public void onFailure(final Throwable throwable) {
            // User/Application RPC failed (The RPC did not reach the remote device or ...)
            // FIXME: what other reasons could cause this ?)
            LOG.warn("{}: Rpc failure detected. Reconnecting netconf session", id, throwable);
            userFuture.setException(throwable);
            // There is no point in keeping this session. Reconnect.
            reconnect();
        }
    }

    /**
     * DOMRpcService proxy that attaches reset-keepalive-task and schedule
     * request-timeout-task to each RPC invocation.
     */
    public final class KeepaliveDOMRpcService implements DOMRpcService {
        private final @NonNull DOMRpcService deviceRpc;

        KeepaliveDOMRpcService(final DOMRpcService deviceRpc) {
            this.deviceRpc = requireNonNull(deviceRpc);
        }

        public @NonNull DOMRpcService getDeviceRpc() {
            return deviceRpc;
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeRpc(final QName type, final NormalizedNode<?, ?> input) {
            keepaliveTask.disableKeepalive();
            final ListenableFuture<? extends DOMRpcResult> deviceFuture = deviceRpc.invokeRpc(type, input);

            final RequestTimeoutTask timeout = new RequestTimeoutTask(deviceFuture);
            final ScheduledFuture<?> timeoutFuture = executor.schedule(timeout, timeoutNanos, TimeUnit.NANOSECONDS);
            deviceFuture.addListener(() -> timeoutFuture.cancel(false), MoreExecutors.directExecutor());

            return timeout.userFuture;
        }

        @Override
        public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(final T listener) {
            // There is no real communication with the device (yet), hence recordActivity() or anything
            return deviceRpc.registerRpcListener(listener);
        }
    }
}

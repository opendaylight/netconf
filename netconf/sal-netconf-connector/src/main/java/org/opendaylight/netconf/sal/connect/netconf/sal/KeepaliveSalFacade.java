/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static com.google.common.base.Preconditions.checkState;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps.getSourceNode;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
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

    private final RemoteDeviceId id;
    private final RemoteDeviceHandler<NetconfSessionPreferences> salFacade;
    private final ScheduledExecutorService executor;
    private final long keepaliveDelaySeconds;
    private final ResetKeepalive resetKeepaliveTask;
    private final long defaultRequestTimeoutMillis;

    private volatile NetconfDeviceCommunicator listener;
    private volatile ScheduledFuture<?> currentKeepalive;
    private volatile DOMRpcService currentDeviceRpc;
    private final AtomicBoolean lastKeepAliveSucceeded = new AtomicBoolean(false);

    public KeepaliveSalFacade(final RemoteDeviceId id, final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
                              final ScheduledExecutorService executor, final long keepaliveDelaySeconds,
                              final long defaultRequestTimeoutMillis) {
        this.id = id;
        this.salFacade = salFacade;
        this.executor = executor;
        this.keepaliveDelaySeconds = keepaliveDelaySeconds;
        this.defaultRequestTimeoutMillis = defaultRequestTimeoutMillis;
        this.resetKeepaliveTask = new ResetKeepalive();
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

    volatile long lastKeepaliveTimeStamp;
    volatile long lastActivityTimeStamp;

    /**
     * Re schedule next keepalive or create new if it exists.
     */
    synchronized void resetKeepalive() {
        LOG.trace("{}: Resetting netconf keepalive timer", id);
        if (currentKeepalive == null || currentKeepalive.isCancelled()) {
            lastKeepAliveSucceeded.set(true);
            checkState(currentDeviceRpc != null);
            LOG.trace("{}: Scheduling keepalives every  {} {}", id, keepaliveDelaySeconds, TimeUnit.SECONDS);
            currentKeepalive = executor.schedule(new Keepalive(), keepaliveDelaySeconds, TimeUnit.SECONDS);
        } else {
            lastActivityTimeStamp = new Date().getTime();
        }
    }

    /**
     * Cancel current keepalive and also reset current deviceRpc.
     */
    private synchronized void stopKeepalives() {
        if (currentKeepalive != null) {
            currentKeepalive.cancel(false);
        }
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
        this.currentDeviceRpc = deviceRpc;
        final DOMRpcService deviceRpc1 =
                new KeepaliveDOMRpcService(deviceRpc, resetKeepaliveTask, defaultRequestTimeoutMillis, executor,
                        new ResponseWaitingScheduler());

        salFacade.onDeviceConnected(remoteSchemaContext, netconfSessionPreferences, deviceRpc1, deviceAction);

        LOG.debug("{}: Netconf session initiated, starting keepalives", id);
        resetKeepalive();
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
        resetKeepalive();
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
    private class Keepalive implements Runnable, FutureCallback<DOMRpcResult> {

        private void proceed() {
            try {
                final boolean lastJobSucceeded = lastKeepAliveSucceeded.getAndSet(false);
                if (!lastJobSucceeded) {
                    onFailure(new IllegalStateException("Previous keepalive timed out"));
                } else {
                    Futures.addCallback(currentDeviceRpc.invokeRpc(NETCONF_GET_CONFIG_PATH, KEEPALIVE_PAYLOAD), this,
                            MoreExecutors.directExecutor());
                }
            } catch (final NullPointerException e) {
                LOG.debug("{}: Skipping keepalive while reconnecting", id);
                // Empty catch block intentional
                // Do nothing. The currentDeviceRpc was null and it means we hit the reconnect window and
                // attempted to send keepalive while we were reconnecting. Next keepalive will be scheduled
                // after reconnect so no action necessary here.
            }
        }

        @Override
        public void run() {
            synchronized (KeepaliveSalFacade.this) {
                LOG.trace("{}: Invoking keepalive RPC", id);

                if (currentKeepalive == null || currentKeepalive.isCancelled()) {
                    return;
                }

                if (lastActivityTimeStamp > lastKeepaliveTimeStamp) {
                    long additionalTime = lastActivityTimeStamp - lastKeepaliveTimeStamp;
                    currentKeepalive = executor.schedule(this, additionalTime, TimeUnit.SECONDS);
                } else {
                    proceed();
                    currentKeepalive = executor.schedule(this, keepaliveDelaySeconds, TimeUnit.SECONDS);
                }
                lastKeepaliveTimeStamp = new Date().getTime();
            }
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
                lastKeepAliveSucceeded.set(true);
            }  else if (result.getErrors() != null) {
                LOG.warn("{}: Keepalive RPC failed with error: {}", id, result.getErrors());
                lastKeepAliveSucceeded.set(true);
            } else {
                LOG.warn("{} Keepalive RPC returned null with response. Reconnecting netconf session", id);
                reconnect();
            }
        }

        @Override
        public void onFailure(final Throwable throwable) {
            LOG.warn("{}: Keepalive RPC failed. Reconnecting netconf session.", id, throwable);
            reconnect();
        }
    }

    /**
     * Reset keepalive after each RPC response received.
     */
    private class ResetKeepalive implements FutureCallback<DOMRpcResult> {
        @Override
        public void onSuccess(final DOMRpcResult result) {
            // No matter what response we got,
            // rpc-reply or rpc-error, we got it from device so the netconf session is OK.
            resetKeepalive();
        }

        @Override
        public void onFailure(final Throwable throwable) {
            // User/Application RPC failed (The RPC did not reach the remote device or ..
            // TODO what other reasons could cause this ?)
            // There is no point in keeping this session. Reconnect.
            LOG.warn("{}: Rpc failure detected. Reconnecting netconf session", id, throwable);
            reconnect();
        }
    }

    private final class ResponseWaitingScheduler {

        private ScheduledFuture<?> schedule;

        public void initScheduler(final Runnable runnable) {
            resetKeepalive();
            //Listening on the result should be done before the keepalive rpc will be send
            final long delay = keepaliveDelaySeconds * 1000 - 500;
            schedule = executor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
        }

        public void stopScheduler() {
            if (schedule != null) {
                schedule.cancel(true);
            } else {
                LOG.trace("Scheduler does not exist.");
            }
        }
    }

    private static final class ResponseWaiting implements Runnable {

        private final ListenableFuture<? extends DOMRpcResult> rpcResultFuture;
        private final ResponseWaitingScheduler responseWaitingScheduler;

        ResponseWaiting(final ResponseWaitingScheduler responseWaitingScheduler,
                final ListenableFuture<? extends DOMRpcResult> rpcResultFuture) {
            this.responseWaitingScheduler = responseWaitingScheduler;
            this.rpcResultFuture = rpcResultFuture;
        }

        public void start() {
            LOG.trace("Start to waiting for result.");
            responseWaitingScheduler.initScheduler(this);
        }

        public void stop() {
            LOG.info("Stop to waiting for result.");
            responseWaitingScheduler.stopScheduler();
        }

        @Override
        public void run() {
            if (!rpcResultFuture.isCancelled() && !rpcResultFuture.isDone()) {
                LOG.trace("Waiting for result");
                responseWaitingScheduler.initScheduler(this);
            } else {
                LOG.trace("Result has been cancelled or done.");
            }
        }
    }

    /*
     * Request timeout task is called once the defaultRequestTimeoutMillis is
     * reached. At this moment, if the request is not yet finished, we cancel
     * it.
     */
    private static final class RequestTimeoutTask implements Runnable {
        private final ListenableFuture<? extends DOMRpcResult> rpcResultFuture;
        private final ResponseWaiting responseWaiting;

        RequestTimeoutTask(final ListenableFuture<? extends DOMRpcResult> rpcResultFuture,
                final ResponseWaiting responseWaiting) {
            this.rpcResultFuture = rpcResultFuture;
            this.responseWaiting = responseWaiting;
        }

        @Override
        public void run() {
            if (!rpcResultFuture.isDone()) {
                rpcResultFuture.cancel(true);
            }
            if (responseWaiting != null) {
                responseWaiting.stop();
            }
        }
    }

    /**
     * DOMRpcService proxy that attaches reset-keepalive-task and schedule
     * request-timeout-task to each RPC invocation.
     */
    public static final class KeepaliveDOMRpcService implements DOMRpcService {
        private final DOMRpcService deviceRpc;
        private final ResetKeepalive resetKeepaliveTask;
        private final long defaultRequestTimeoutMillis;
        private final ScheduledExecutorService executor;
        private final ResponseWaitingScheduler responseWaitingScheduler;

        KeepaliveDOMRpcService(final DOMRpcService deviceRpc, final ResetKeepalive resetKeepaliveTask,
                final long defaultRequestTimeoutMillis, final ScheduledExecutorService executor,
                final ResponseWaitingScheduler responseWaitingScheduler) {
            this.deviceRpc = deviceRpc;
            this.resetKeepaliveTask = resetKeepaliveTask;
            this.defaultRequestTimeoutMillis = defaultRequestTimeoutMillis;
            this.executor = executor;
            this.responseWaitingScheduler = responseWaitingScheduler;
        }

        public DOMRpcService getDeviceRpc() {
            return deviceRpc;
        }

        @Override
        public ListenableFuture<? extends DOMRpcResult> invokeRpc(final SchemaPath type,
                final NormalizedNode<?, ?> input) {
            final ListenableFuture<? extends DOMRpcResult> rpcResultFuture = deviceRpc.invokeRpc(type, input);
            final ResponseWaiting responseWaiting = new ResponseWaiting(responseWaitingScheduler, rpcResultFuture);
            responseWaiting.start();
            Futures.addCallback(rpcResultFuture, resetKeepaliveTask, MoreExecutors.directExecutor());

            final RequestTimeoutTask timeoutTask = new RequestTimeoutTask(rpcResultFuture, responseWaiting);
            executor.schedule(timeoutTask, defaultRequestTimeoutMillis, TimeUnit.MILLISECONDS);

            return rpcResultFuture;
        }

        @Override
        public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(final T listener) {
            // There is no real communication with the device (yet), no reset here
            return deviceRpc.registerRpcListener(listener);
        }
    }
}

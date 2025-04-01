/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.EOFException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfMessageUtil;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceCommunicator implements NetconfClientSessionListener, RemoteDeviceCommunicator {
    private record Request(
            @NonNull UncancellableFuture<RpcResult<NetconfMessage>> future,
            @NonNull NetconfMessage request) {
        Request {
            requireNonNull(future);
            requireNonNull(request);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceCommunicator.class);
    private static final VarHandle CLOSING;

    static {
        try {
            CLOSING = MethodHandles.lookup().findVarHandle(NetconfDeviceCommunicator.class, "closing", boolean.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected final RemoteDevice<NetconfDeviceCommunicator> remoteDevice;
    private final @Nullable UserPreferences overrideNetconfCapabilities;
    protected final RemoteDeviceId id;
    private final Lock sessionLock = new ReentrantLock();
    private final Lock remoteDeviceLock = new ReentrantLock();

    private final Semaphore semaphore;
    private final int concurentRpcMsgs;

    private final Queue<Request> requests = new ArrayDeque<>();
    private NetconfClientSession currentSession;

    // isSessionClosing indicates a close operation on the session is issued and tearDown will surely be called later
    // to finish the close.
    // Used to allow only one thread to enter tearDown and other threads should NOT enter it simultaneously and should
    // end its close operation without calling tearDown to release the locks they hold to avoid deadlock.
    //
    // This field is manipulated using CLOSING VarHandle
    @SuppressWarnings("unused")
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile boolean closing;

    public boolean isSessionClosing() {
        return (boolean) CLOSING.getVolatile(this);
    }

    public NetconfDeviceCommunicator(final RemoteDeviceId id,
            final RemoteDevice<NetconfDeviceCommunicator> remoteDevice, final int rpcMessageLimit) {
        this(id, remoteDevice, rpcMessageLimit, null);
    }

    public NetconfDeviceCommunicator(final RemoteDeviceId id,
            final RemoteDevice<NetconfDeviceCommunicator> remoteDevice, final int rpcMessageLimit,
            final @Nullable UserPreferences overrideNetconfCapabilities) {
        concurentRpcMsgs = rpcMessageLimit;
        this.id = id;
        this.remoteDevice = remoteDevice;
        this.overrideNetconfCapabilities = overrideNetconfCapabilities;
        semaphore = rpcMessageLimit > 0 ? new Semaphore(rpcMessageLimit) : null;
    }

    @Override
    public void onSessionUp(final NetconfClientSession session) {
        final NetconfSessionPreferences remoteSessionCapabilities;
        sessionLock.lock();
        try {
            LOG.debug("{}: Session established", id);
            currentSession = session;

            var netconfSessionPreferences = NetconfSessionPreferences.fromNetconfSession(session);
            LOG.trace("{}: Session advertised capabilities: {}", id, netconfSessionPreferences);

            final var localOverride = overrideNetconfCapabilities;
            if (localOverride != null) {
                final var sessionPreferences = localOverride.sessionPreferences();
                netconfSessionPreferences = localOverride.overrideModuleCapabilities()
                        ? netconfSessionPreferences.replaceModuleCaps(sessionPreferences)
                        : netconfSessionPreferences.addModuleCaps(sessionPreferences);

                netconfSessionPreferences = localOverride.overrideNonModuleCapabilities()
                        ? netconfSessionPreferences.replaceNonModuleCaps(sessionPreferences)
                        : netconfSessionPreferences.addNonModuleCaps(sessionPreferences);
                LOG.debug("{}: Session capabilities overridden, capabilities that will be used: {}", id,
                        netconfSessionPreferences);
            }
            remoteSessionCapabilities = netconfSessionPreferences;
        } finally {
            sessionLock.unlock();
        }
        remoteDeviceLock.lock();
        try {
            remoteDevice.onRemoteSessionUp(remoteSessionCapabilities, this);
        } finally {
            remoteDeviceLock.unlock();
        }
    }

    public void disconnect() {
        // If session is already in closing, no need to close it again
        if (currentSession != null && CLOSING.compareAndSet(this, false, true) && currentSession.isUp()) {
            currentSession.close();
        }
    }

    private void tearDown(final String reason) {
        if (!isSessionClosing()) {
            LOG.warn("It's curious that no one to close the session but tearDown is called!");
        }
        LOG.debug("Tearing down {}", reason);
        final var futuresToCancel = new ArrayList<UncancellableFuture<RpcResult<NetconfMessage>>>();
        boolean isCurrentSessionClosed = false;
        sessionLock.lock();
        try {
            if (currentSession != null) {
                currentSession = null;
                isCurrentSessionClosed = true;
                /*
                 * Walk all requests, check if they have been executing
                 * or cancelled and remove them from the queue.
                 */
                final var it = requests.iterator();
                while (it.hasNext()) {
                    final var r = it.next();
                    futuresToCancel.add(r.future);
                    it.remove();
                    // we have just removed one request from the queue
                    // we can also release one permit
                    if (semaphore != null) {
                        semaphore.release();
                    }
                }
            }
        } finally {
            sessionLock.unlock();
        }
        if (isCurrentSessionClosed) {
            remoteDeviceLock.lock();
            try {
                remoteDevice.onRemoteSessionDown();
            } finally {
                remoteDeviceLock.unlock();
            }
        }
        // Notify pending request futures outside of the sessionLock to avoid unnecessarily blocking the caller.
        for (var future : futuresToCancel) {
            if (Strings.isNullOrEmpty(reason)) {
                future.set(createSessionDownRpcResult());
            } else {
                future.set(createErrorRpcResult(ErrorType.TRANSPORT, reason));
            }
        }

        CLOSING.setVolatile(this, false);
    }

    private RpcResult<NetconfMessage> createSessionDownRpcResult() {
        return createErrorRpcResult(ErrorType.TRANSPORT,
            "The netconf session to %1$s is disconnected".formatted(id.name()));
    }

    private static RpcResult<NetconfMessage> createErrorRpcResult(final ErrorType errorType, final String message) {
        return RpcResultBuilder.<NetconfMessage>failed()
            .withError(errorType, ErrorTag.OPERATION_FAILED, message)
            .build();
    }

    @Override
    public void onSessionDown(final NetconfClientSession session, final Exception exception) {
        // If session is already in closing, no need to call tearDown again.
        if (CLOSING.compareAndSet(this, false, true)) {
            if (exception instanceof EOFException) {
                LOG.info("{}: Session went down: {}", id, exception.getMessage());
            } else {
                LOG.warn("{}: Session went down", id, exception);
            }
            tearDown(null);
        }
    }

    @Override
    public void onSessionTerminated(final NetconfClientSession session, final NetconfTerminationReason reason) {
        // onSessionTerminated is called directly by disconnect, no need to compare and set isSessionClosing.
        LOG.warn("{}: Session terminated {}", id, reason);
        tearDown(reason.getErrorMessage());
    }

    @Override
    public void close() {
        // Disconnect from device
        // tear down not necessary, called indirectly by the close in disconnect()
        disconnect();
    }

    @Override
    public void onMessage(final NetconfClientSession session, final NetconfMessage message) {
        // Dispatch between notifications and messages. Messages need to be processed with lock held, notifications do
        // not.
        if (NotificationMessage.ELEMENT_NAME.equals(XmlElement.fromDomDocument(message.getDocument()).getName())) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("{}: Notification received: {}", id, message);
            }
            remoteDeviceLock.lock();
            try {
                remoteDevice.onNotification(message);
            } finally {
                remoteDeviceLock.unlock();
            }
        } else {
            processMessage(message);
        }
    }

    @Override
    public void onError(final NetconfClientSession session, final Exception failure) {
        final var request = pollRequest();
        if (request != null) {
            request.future.set(RpcResultBuilder.<NetconfMessage>failed()
                .withRpcError(toRpcError(new DocumentedException(failure.getMessage(),
                    ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.ERROR)))
                .build());
        } else {
            LOG.warn("{}: Ignoring unsolicited failure {}", id, failure.toString());
        }
    }

    private @Nullable Request pollRequest() {
        sessionLock.lock();
        try {
            var request = requests.peek();
            if (request != null) {
                request = requests.poll();
                // we have just removed one request from the queue
                // we can also release one permit
                if (semaphore != null) {
                    semaphore.release();
                }
                return request;
            }
            return null;
        } finally {
            sessionLock.unlock();
        }
    }

    private void processMessage(final NetconfMessage message) {
        final var request = pollRequest();
        if (request == null) {
            // No matching request, bail out
            if (LOG.isWarnEnabled()) {
                LOG.warn("{}: Ignoring unsolicited message {}", id, msgToS(message));
            }
            return;
        }

        LOG.debug("{}: Message received {}", id, message);
        if (LOG.isTraceEnabled()) {
            LOG.trace("{}: Matched request: {} to response: {}", id, msgToS(request.request), msgToS(message));
        }

        final var inputMsgId = request.request.getDocument().getDocumentElement()
            .getAttribute(XmlNetconfConstants.MESSAGE_ID);
        final var outputMsgId = message.getDocument().getDocumentElement()
            .getAttribute(XmlNetconfConstants.MESSAGE_ID);
        if (!inputMsgId.equals(outputMsgId)) {
            // FIXME: we should be able to transform directly to RpcError without an intermediate exception
            final var ex = new DocumentedException("Response message contained unknown \"message-id\"", null,
                ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE, ErrorSeverity.ERROR,
                ImmutableMap.of("actual-message-id", outputMsgId, "expected-message-id", inputMsgId));
            if (LOG.isWarnEnabled()) {
                LOG.warn("{}: Invalid request-reply match, reply message contains different message-id, request: {}, "
                    + "response: {}", id, msgToS(request.request), msgToS(message));
            }
            request.future.set(RpcResultBuilder.<NetconfMessage>failed().withRpcError(toRpcError(ex)).build());

            // recursively processing message to eventually find matching request
            processMessage(message);
            return;
        }

        final RpcResult<NetconfMessage> result;
        if (NetconfMessageUtil.isErrorMessage(message)) {
            // FIXME: we should be able to transform directly to RpcError without an intermediate exception
            final var ex = DocumentedException.fromXMLDocument(message.getDocument());
            if (LOG.isWarnEnabled()) {
                LOG.warn("{}: Error reply from remote device, request: {}, response: {}", id, msgToS(request.request),
                    msgToS(message));
            }
            result = RpcResultBuilder.<NetconfMessage>failed().withRpcError(toRpcError(ex)).build();
        } else {
            result = RpcResultBuilder.success(message).build();
        }

        request.future.set(result);
    }

    private static String msgToS(final NetconfMessage msg) {
        return XmlUtil.toString(msg.getDocument());
    }

    private static RpcError toRpcError(final DocumentedException ex) {
        final var errorInfo = ex.getErrorInfo();
        final String infoString;
        if (errorInfo != null) {
            final var sb = new StringBuilder();
            for (var e : errorInfo.entrySet()) {
                final var tag = e.getKey();
                sb.append('<').append(tag).append('>').append(e.getValue()).append("</").append(tag).append('>');
            }
            infoString = sb.toString();
        } else {
            infoString = "";
        }

        return ex.getErrorSeverity() == ErrorSeverity.ERROR
            ? RpcResultBuilder.newError(ex.getErrorType(), ex.getErrorTag(), ex.getLocalizedMessage(), null,
                infoString, ex.getCause())
            : RpcResultBuilder.newWarning(ex.getErrorType(), ex.getErrorTag(), ex.getLocalizedMessage(), null,
                infoString, ex.getCause());
    }

    @Override
    public ListenableFuture<RpcResult<NetconfMessage>> sendRequest(final NetconfMessage message) {
        sessionLock.lock();
        try {
            if (currentSession == null) {
                LOG.warn("{}: Session is disconnected, failing RPC request {}", id, message);
                return Futures.immediateFuture(createSessionDownRpcResult());
            }
            if (semaphore != null && !semaphore.tryAcquire()) {
                LOG.warn("Limit of concurrent rpc messages was reached (limit: {}). Rpc reply message is needed. "
                    + "Discarding request of Netconf device with id: {}", concurentRpcMsgs, id.name());
                return Futures.immediateFailedFuture(new DocumentedException(
                        "Limit of rpc messages was reached (Limit :" + concurentRpcMsgs
                        + ") waiting for emptying the queue of Netconf device with id: " + id.name()));
            }

            return sendRequestWithLock(message);
        } finally {
            sessionLock.unlock();
        }
    }

    private ListenableFuture<RpcResult<NetconfMessage>> sendRequestWithLock(final NetconfMessage message) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("{}: Sending message {}", id, msgToS(message));
        }

        final var req = new Request(new UncancellableFuture<>(), message);
        requests.add(req);

        currentSession.sendMessage(req.request).addListener(future -> {
            final var cause = future.cause();
            if (cause != null) {
                // We expect that a session down will occur at this point
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{}: Failed to send request {}", id, XmlUtil.toString(req.request.getDocument()), cause);
                }

                req.future.set(createErrorRpcResult(ErrorType.TRANSPORT, cause.getLocalizedMessage()));
            } else {
                LOG.trace("Finished sending request {}", req.request);
            }
        });

        return req.future;
    }
}

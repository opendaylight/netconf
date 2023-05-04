/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.client.stress;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SyncExecutionStrategy extends AbstractExecutionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(SyncExecutionStrategy.class);

    SyncExecutionStrategy(final Parameters params, final List<NetconfMessage> preparedMessages,
                          final NetconfDeviceCommunicator sessionListener) {
        super(params, preparedMessages, sessionListener);
    }

    @Override
    public void invoke() {
        final AtomicInteger responseCounter = new AtomicInteger(0);

        int batchI = 0;
        for (final Integer editBatch : getEditBatches()) {
            for (int i = 0; i < editBatch; i++) {
                final int msgId = i + batchI * getParams().editBatchSize;
                final NetconfMessage msg = getPreparedMessages().get(msgId);
                LOG.debug("Sending message {}", msgId);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending message {}", XmlUtil.toString(msg.getDocument()));
                }
                final ListenableFuture<RpcResult<NetconfMessage>> netconfMessageFuture =
                        getSessionListener().sendRequest(msg, StressClient.EDIT_QNAME);
                // Wait for response
                waitForResponse(responseCounter, netconfMessageFuture);

            }
            batchI++;
            LOG.info("Batch {} with size {} sent. Committing", batchI, editBatch);

            // Commit batch sync
            if (getParams().candidateDatastore) {
                waitForResponse(responseCounter,
                        getSessionListener().sendRequest(StressClient.COMMIT_MSG, StressClient.COMMIT_QNAME));
            }
        }

        Preconditions.checkState(
            responseCounter.get() == getEditAmount() + (getParams().candidateDatastore ? getEditBatches().size() : 0),
            "Not all responses were received, only %s from %s",
            responseCounter.get(), getParams().editCount + getEditBatches().size());
    }

    private void waitForResponse(final AtomicInteger responseCounter,
                                 final ListenableFuture<RpcResult<NetconfMessage>> netconfMessageFuture) {
        try {
            final RpcResult<NetconfMessage> netconfMessageRpcResult =
                    netconfMessageFuture.get(getParams().msgTimeout, TimeUnit.SECONDS);
            if (netconfMessageRpcResult.isSuccessful()) {
                responseCounter.incrementAndGet();
                LOG.debug("Received response {}", responseCounter.get());
            } else {
                LOG.warn("Request failed {}", netconfMessageRpcResult);
            }

        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        } catch (final ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Request not finished", e);
        }
    }
}

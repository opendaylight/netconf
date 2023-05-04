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
import java.util.ArrayList;
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

class AsyncExecutionStrategy extends AbstractExecutionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncExecutionStrategy.class);

    AsyncExecutionStrategy(final Parameters params, final List<NetconfMessage> editConfigMsgs,
                           final NetconfDeviceCommunicator sessionListener) {
        super(params, editConfigMsgs, sessionListener);
    }

    @Override
    public void invoke() {
        final AtomicInteger responseCounter = new AtomicInteger(0);
        final List<ListenableFuture<RpcResult<NetconfMessage>>> futures = new ArrayList<>();

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
                futures.add(netconfMessageFuture);
            }
            batchI++;
            LOG.info("Batch {} with size {} sent. Committing", batchI, editBatch);
            if (getParams().candidateDatastore) {
                futures.add(getSessionListener().sendRequest(StressClient.COMMIT_MSG, StressClient.COMMIT_QNAME));
            }
        }

        LOG.info("All batches sent. Waiting for responses");
        // Wait for every future
        for (final ListenableFuture<RpcResult<NetconfMessage>> future : futures) {
            try {
                final RpcResult<NetconfMessage> netconfMessageRpcResult = future.get(
                    getParams().msgTimeout, TimeUnit.SECONDS);
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

        Preconditions.checkState(
            responseCounter.get() == getEditAmount() + (getParams().candidateDatastore ? getEditBatches().size() : 0),
            "Not all responses were received, only %s from %s",
            responseCounter.get(), getParams().editCount + getEditBatches().size());
    }
}

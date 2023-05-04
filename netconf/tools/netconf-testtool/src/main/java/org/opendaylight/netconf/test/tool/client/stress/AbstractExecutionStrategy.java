/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.client.stress;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;

abstract class AbstractExecutionStrategy implements ExecutionStrategy {
    private final Parameters params;
    private final List<NetconfMessage> preparedMessages;
    private final NetconfDeviceCommunicator sessionListener;
    private final List<Integer> editBatches;
    private final int editAmount;

    AbstractExecutionStrategy(final Parameters params, final List<NetconfMessage> editConfigMsgs,
                              final NetconfDeviceCommunicator sessionListener) {
        editAmount = editConfigMsgs.size();
        this.params = params;
        this.preparedMessages = editConfigMsgs;
        this.sessionListener = sessionListener;
        this.editBatches = countEditBatchSizes(params, editConfigMsgs.size());
    }

    private static List<Integer> countEditBatchSizes(final Parameters params, final int amount) {
        final List<Integer> editBatches = new ArrayList<>();
        if (params.editBatchSize != amount) {
            final int fullBatches = amount / params.editBatchSize;
            for (int i = 0; i < fullBatches; i++) {
                editBatches.add(params.editBatchSize);
            }

            if (amount % params.editBatchSize != 0) {
                editBatches.add(amount % params.editBatchSize);
            }
        } else {
            editBatches.add(params.editBatchSize);
        }
        return editBatches;
    }


    protected Parameters getParams() {
        return params;
    }

    protected List<NetconfMessage> getPreparedMessages() {
        return preparedMessages;
    }

    protected NetconfDeviceCommunicator getSessionListener() {
        return sessionListener;
    }

    protected List<Integer> getEditBatches() {
        return editBatches;
    }

    protected int getEditAmount() {
        return editAmount;
    }
}

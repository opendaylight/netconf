/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages.transactions;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;

public class PutRequest implements TransactionRequest {

    private final LogicalDatastoreType store;
    private final NormalizedNodeMessage data;

    public PutRequest(final LogicalDatastoreType store, final NormalizedNodeMessage data) {
        this.store = store;
        this.data = data;
    }

    public NormalizedNodeMessage getNormalizedNodeMessage() {
        return data;
    }

    public LogicalDatastoreType getStore() {
        return store;
    }
}

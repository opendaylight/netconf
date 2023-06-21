/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.transactions;

import java.io.Serial;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class DeleteRequest implements WriteActorMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private final LogicalDatastoreType store;
    private final YangInstanceIdentifier path;

    public DeleteRequest(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        this.store = store;
        this.path = path;
    }

    public LogicalDatastoreType getStore() {
        return store;
    }

    public YangInstanceIdentifier getPath() {
        return path;
    }
}

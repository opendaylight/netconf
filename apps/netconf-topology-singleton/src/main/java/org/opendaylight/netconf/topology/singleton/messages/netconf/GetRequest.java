/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.netconf;

import java.io.Serial;
import java.util.List;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadActorMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class GetRequest implements ReadActorMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private final LogicalDatastoreType store;
    private final YangInstanceIdentifier path;
    private final List<YangInstanceIdentifier> fields;

    public GetRequest(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        this.store = store;
        this.path = path;
        this.fields = fields;
    }

    public LogicalDatastoreType store() {
        return store;
    }

    public YangInstanceIdentifier path() {
        return path;
    }

    public List<YangInstanceIdentifier> fields() {
        return fields;
    }
}

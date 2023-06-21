/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.netconf;

import java.io.Serial;
import java.io.Serializable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class RemoveEditConfigRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final LogicalDatastoreType store;
    private final YangInstanceIdentifier path;

    public RemoveEditConfigRequest(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
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

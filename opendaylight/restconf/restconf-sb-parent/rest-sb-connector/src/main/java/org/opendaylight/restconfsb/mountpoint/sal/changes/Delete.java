/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal.changes;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

class Delete extends Change {

    Delete(final RestconfFacade facade, final YangInstanceIdentifier path) {
        super(path, null, facade);
    }

    @Override
    public ListenableFuture<Void> apply(final Void input) {
        return facade.deleteConfig(this.getPath());
    }
}

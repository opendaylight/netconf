/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.api;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import scala.concurrent.Future;

public interface NetconfDOMTransactions {
    Future<Optional<NormalizedNodeMessage>> read(LogicalDatastoreType store, YangInstanceIdentifier path);

    Future<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);

    void put(LogicalDatastoreType store, NormalizedNodeMessage data);

    void merge(LogicalDatastoreType store, NormalizedNodeMessage data);

    void delete(LogicalDatastoreType store, YangInstanceIdentifier path);

    boolean cancel();

    Future<Void> submit();
}

/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
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

/**
 * Provides API for read transaction operations
 */
public interface NetconfDOMReadTransaction {

    /**
     * Read data from particular data-store
     *
     * @param store data-store type
     * @param path  unique identifier of a particular node instance in the data tree
     * @return result as future
     */
    Future<Optional<NormalizedNodeMessage>> read(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Test existence of node in certain data-store
     *
     * @param store data-store type
     * @param path  unique identifier of a particular node instance in the data tree
     * @return result as future
     */
    Future<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);
}

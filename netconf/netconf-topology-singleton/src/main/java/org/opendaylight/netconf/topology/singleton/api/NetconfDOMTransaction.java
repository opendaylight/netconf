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

/**
 * Provides API for all operations of read and write transactions
 */
public interface NetconfDOMTransaction {

    /**
     * Read data from particular data-store
     * @param store data-store type
     * @param path unique identifier of a particular node instance in the data tree
     * @return result as future
     */
    Future<Optional<NormalizedNodeMessage>> read(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Test existence of node in certain data-store
     * @param store data-store type
     * @param path unique identifier of a particular node instance in the data tree
     * @return result as future
     */
    Future<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Put data to particular data-store
     * @param store data-store type
     * @param data data for inserting included in NormalizedNodeMessage object
     */
    void put(LogicalDatastoreType store, NormalizedNodeMessage data);

    /**
     * Merge data with existing node in particular data-store
     * @param store data-store type
     * @param data data for merging included in NormalizedNodeMessage object
     */
    void merge(LogicalDatastoreType store, NormalizedNodeMessage data);

    /**
     * Delete node in particular data-store in path
     * @param store data-store type
     * @param path unique identifier of a particular node instance in the data tree
     */
    void delete(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Cancel operation
     * @return success or not
     */
    boolean cancel();

    /**
     * Commit opened transaction.
     * @return void or raised exception
     */
    Future<Void> submit();
}

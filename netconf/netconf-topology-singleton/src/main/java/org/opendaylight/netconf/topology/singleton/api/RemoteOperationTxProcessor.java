/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.api;

import akka.actor.ActorRef;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Provides API for remote calling operations of transactions. Salve sends message of particular
 * operation to master and master performs it.
 */
public interface RemoteOperationTxProcessor {

    /**
     * Delete node in particular data-store in path
     * @param store data-store type
     * @param path unique identifier of a particular node instance in the data tree
     */
    void doDelete(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Commit opened transaction.
     * @param recipient recipient of submit result
     * @param sender sender of submit result
     */
    void doSubmit(ActorRef recipient, ActorRef sender);

    /**
     * Cancel operation
     * @param recipient recipient of cancel result
     * @param sender sender of cancel result
     */
    void doCancel(ActorRef recipient, ActorRef sender);

    /**
     * Put data to particular data-store
     * @param store data-store type
     * @param data data for inserting included in NormalizedNodeMessage object
     */
    void doPut(LogicalDatastoreType store, NormalizedNodeMessage data);

    /**
     * Merge data with existing node in particular data-store
     * @param store data-store type
     * @param data data for merging included in NormalizedNodeMessage object
     */
    void doMerge(LogicalDatastoreType store, NormalizedNodeMessage data);

    /**
     * Read data from particular data-store
     * @param store data-store type
     * @param path unique identifier of a particular node instance in the data tree
     * @param recipient recipient of read result
     * @param sender sender of read result
     */
    void doRead(LogicalDatastoreType store, YangInstanceIdentifier path, ActorRef recipient, ActorRef sender);

    /**
     * Test existence of node in certain data-store
     * @param store data-store type
     * @param path unique identifier of a particular node instance in the data tree
     * @param recipient recipient of exists result
     * @param sender sender of exists result
     */
    void doExists(LogicalDatastoreType store, YangInstanceIdentifier path, ActorRef recipient, ActorRef sender);

}

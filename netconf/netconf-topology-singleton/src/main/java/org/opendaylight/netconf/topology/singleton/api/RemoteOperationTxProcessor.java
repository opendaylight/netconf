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

public interface RemoteOperationTxProcessor {

    void doDelete(LogicalDatastoreType store, YangInstanceIdentifier path);

    void doSubmit(ActorRef recipient, ActorRef sender);

    void doCancel(ActorRef recipient, ActorRef sender);

    void doPut(LogicalDatastoreType store, NormalizedNodeMessage data);

    void doMerge(LogicalDatastoreType store, NormalizedNodeMessage data);

    void doRead(LogicalDatastoreType store, YangInstanceIdentifier path, ActorRef recipient, ActorRef sender);

    void doExists(LogicalDatastoreType store, YangInstanceIdentifier path, ActorRef recipient, ActorRef sender);

}

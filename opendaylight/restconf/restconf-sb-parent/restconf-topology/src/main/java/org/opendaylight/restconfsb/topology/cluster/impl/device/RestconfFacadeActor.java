/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import akka.actor.ActorRef;
import akka.actor.TypedActor;
import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.restconfsb.topology.cluster.impl.messages.RpcMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import scala.concurrent.Future;

/**
 * Restconf facade actor enables remote actors to communicate with restconf device connected on another peer of the cluster.
 */
public interface RestconfFacadeActor extends TypedActor.Receiver {

    /**
     * Calls HTTP HEAD via master mount point on device datastore.
     * @param datastore datastore
     * @param path path
     * @return successful future, if data exists
     */
    Future<Void> headData(LogicalDatastoreType datastore, YangInstanceIdentifier path);

    /**
     * Calls HTTP GET via master mount point on device datastore.
     * @param datastore datastore
     * @param path path
     * @return data
     */
    Future<Optional<NormalizedNodeMessage>> getData(LogicalDatastoreType datastore, YangInstanceIdentifier path);

    /**
     * Calls HTTP POST via master mount point to invoke operation on device.
     * @param message rpc
     * @return rpc reply
     */
    Future<Optional<RpcMessage>> postOperation(RpcMessage message);

    /**
     * Calls HTTP POST via master mount point on device config datastore.
     *
     * @param message data
     * @return successful future, if post was successful
     */
    Future<Void> postConfig(NormalizedNodeMessage message);

    /**
     * Calls HTTP PUT via master mount point on device config datastore.
     *
     * @param message data
     * @return successful future, if put was successful
     */
    Future<Void> putConfig(NormalizedNodeMessage message);

    /**
     * Calls HTTP PATCH via master mount point on device config datastore.
     *
     * @param message data
     * @return successful future, if patch was successful
     */
    Future<Void> patchConfig(NormalizedNodeMessage message);

    /**
     * Calls HTTP DELETE via master mount point on device config datastore.
     *
     * @param path path
     * @return successful future, if delete was successful
     */
    Future<Void> deleteConfig(YangInstanceIdentifier path);

    /**
     * Provided {@link ActorRef} will be notified about notifications received via
     * {@link org.opendaylight.restconfsb.topology.cluster.impl.messages.NotificationMessage}.
     *
     * @param subscriber notification listener actor
     */
    void subscribe(ActorRef subscriber);
}

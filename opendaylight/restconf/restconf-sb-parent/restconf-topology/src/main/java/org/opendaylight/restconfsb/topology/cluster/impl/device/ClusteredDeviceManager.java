/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;

/**
 * Manages device mount point on the one peer of the cluster.
 */
public interface ClusteredDeviceManager {

    /**
     * Returns list of modules supported by the device.
     * @return modules
     */
    ListenableFuture<List<Module>> getSupportedModules(final RestconfNode restconfNode);

    /**
     * Registers master mount point on this peer of the cluster. It means, that this mount point communicates directly
     * with the device.
     * @param actorSystem actor system
     * @param context actor context
     */
    void registerMasterMountPoint(final ActorSystem actorSystem, final ActorContext context);

    /**
     * Registers slave mount point on this peer of the cluster. It doesn't communicate directly with the device, but instead
     * it routes communication to the device via provided {@link ActorRef} to the master. Master handles communication with
     * the device.
     * @param actorSystem actor system
     * @param context actor context
     * @param masterRef master actor
     */
    void registerSlaveMountPoint(final ActorSystem actorSystem, final ActorContext context, final ActorRef masterRef);

    /**
     * Unregisters mountpoint. If this is master device manager, announces other peers about it.
     */
    void unregisterMountPoint();

}

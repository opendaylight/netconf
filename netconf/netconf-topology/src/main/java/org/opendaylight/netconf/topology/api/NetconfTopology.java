/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.api;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.netconf.nativ.netconf.communicator.util.NetconfDeviceCapabilities;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public interface NetconfTopology {

    ListenableFuture<NetconfDeviceCapabilities> connectNode(NodeId nodeId, Node configNode);

    ListenableFuture<Void> disconnectNode(NodeId nodeId);

}

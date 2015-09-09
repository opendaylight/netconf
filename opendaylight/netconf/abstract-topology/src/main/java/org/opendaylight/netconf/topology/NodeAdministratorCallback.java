/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Augmentation;

public interface NodeAdministratorCallback<NA extends Augmentation<Node>, M> extends NodeAdministrator<NA> {

    // TODO how to handle peer communication on NodeAdmin callback level ?

    void setPeerContext(Peer.PeerContext<M> peerContext);

    void handle(M msg);

}

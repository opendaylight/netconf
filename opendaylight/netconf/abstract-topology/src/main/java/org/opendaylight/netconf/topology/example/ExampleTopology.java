/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.StateAggregator.SingleStateAggregator;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;

public class ExampleTopology {

    private final BaseTopologyManager<UserDefinedMessage> netconfNodeBaseTopologyManager;
    private Peer.PeerContext<UserDefinedMessage> peerCtx;

    public ExampleTopology(final DataBroker dataBroker) {
        final NodeManagerCallbackFactory<UserDefinedMessage> nodeManagerCallbackFactory = new NodeManagerCallbackFactory<UserDefinedMessage>() {
            @Override
            public NodeManagerCallback<UserDefinedMessage> create() {
                // TODO we need to inject the topologyDispatcher into these callbacks
                return new ExampleNodeManagerCallback(null);
            }
        };

        //User needs to implement own StateAggergator
        final ExampleTopologyManagerCallback exampleTopologyManagerCallback = new ExampleTopologyManagerCallback(dataBroker,
                "topology-netconf", nodeManagerCallbackFactory, new SingleStateAggregator());


        netconfNodeBaseTopologyManager = new BaseTopologyManager<>(dataBroker, "topology-netconf",
                exampleTopologyManagerCallback, new SingleStateAggregator());
    }

}

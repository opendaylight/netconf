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
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;

public class ExampleTopology {

    private final BaseTopologyManager<CustomMessage> netconfNodeBaseTopologyManager;
    private Peer.PeerContext<CustomMessage> peerCtx;

    public ExampleTopology(final DataBroker dataBroker) {
        final NodeManagerCallbackFactory<CustomMessage> nodeManagerCallbackFactory = new NodeManagerCallbackFactory<CustomMessage>() {
            @Override
            public NodeManagerCallback<CustomMessage> create() {
                // TODO we need to inject the topologyDispatcher into these callbacks
                return new ExampleNodeManagerCallback(null);
            }
        };

        final TopologyManagerCallbackFactory<CustomMessage> topologyManagerCallbackFactory = new TopologyManagerCallbackFactory<CustomMessage>() {
            @Override
            public TopologyManagerCallback<CustomMessage> create() {
                return new ExampleTopologyManagerCallback(dataBroker, "topology-netconf", nodeManagerCallbackFactory, new SingleStateAggregator());
            }
        };

        netconfNodeBaseTopologyManager = new BaseTopologyManager<>(dataBroker, "topology-netconf",
                topologyManagerCallbackFactory.create(), new SingleStateAggregator());
    }

    public static final class CustomMessage {}
}

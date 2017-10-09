/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netconf.topology.api.NetconfConnectorFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.HostBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by adetalhouet on 2016-11-03.
 */
public class NetconfConnectorFactoryImpl implements NetconfConnectorFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConnectorFactoryImpl.class);

    private static final InstanceIdentifier<Topology> TOPOLOGY_PATH = InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId("topology-netconf")));

    @Override
    public Node newInstance(final DataBroker dataBroker,
                            final String instanceName,
                            final String address,
                            final Integer port,
                            final String username,
                            final String password,
                            final Boolean tcpOnly,
                            final Boolean reconnectOnSchemaChange) {

        final NodeId nodeId = new NodeId(instanceName);
        final NodeKey nodeKey = new NodeKey(nodeId);
        final Credentials credentials = new LoginPasswordBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();
        final Host host = HostBuilder.getDefaultInstance(address);
        final PortNumber portNumber = new PortNumber(port);
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(host)
                .setPort(portNumber)
                .setCredentials(credentials)
                .setTcpOnly(tcpOnly)
                .setReconnectOnChangedSchema(reconnectOnSchemaChange)
                .build();
        final Node node =  new NodeBuilder()
                .setNodeId(nodeId)
                .setKey(nodeKey)
                .addAugmentation(NetconfNode.class, netconfNode)
                .build();

        final InstanceIdentifier<Node> nodePath = TOPOLOGY_PATH.child(Node.class, nodeKey);
        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        transaction.put(LogicalDatastoreType.CONFIGURATION, nodePath, node);
        final CheckedFuture<Void, TransactionCommitFailedException> submitFuture = transaction.submit();
        Futures.addCallback(submitFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {
                LOG.debug("Node {} was successfully added to the topology", instanceName);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Node {} creation failed: {}", instanceName, throwable);
            }
        }, MoreExecutors.directExecutor());
        return node;
    }
}
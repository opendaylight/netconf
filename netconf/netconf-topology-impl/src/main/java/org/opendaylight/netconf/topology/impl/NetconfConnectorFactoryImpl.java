/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.topology.api.NetconfConnectorFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint16;
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

        final NodeKey nodeKey = new NodeKey(new NodeId(instanceName));
        final Node node =  new NodeBuilder()
                .withKey(nodeKey)
                .addAugmentation(new NetconfNodeBuilder()
                    .setHost(createHost(address))
                    .setPort(new PortNumber(Uint16.valueOf(port)))
                    .setCredentials(new LoginPwBuilder()
                        .setLoginPassword(new LoginPasswordBuilder()
                            .setUsername(username)
                            .setPassword(password)
                            .build())
                        .build())
                    .setTcpOnly(tcpOnly)
                    .setReconnectOnChangedSchema(reconnectOnSchemaChange)
                    .build())
                .build();

        final InstanceIdentifier<Node> nodePath = TOPOLOGY_PATH.child(Node.class, nodeKey);
        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        transaction.put(LogicalDatastoreType.CONFIGURATION, nodePath, node);
        transaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Node {} was successfully added to the topology", instanceName);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Node {} creation failed", instanceName, throwable);
            }
        }, MoreExecutors.directExecutor());
        return node;
    }

    private static Host createHost(final String host) {
        try {
            return new Host(new IpAddress(new Ipv4Address(host)));
        } catch (IllegalArgumentException e) {
            LOG.debug("Cannot interpret {} as an Ipv4Address", host, e);
        }
        try {
            return new Host(new IpAddress(new Ipv6Address(host)));
        } catch (IllegalArgumentException e) {
            LOG.debug("Cannot interpret {} as an Ipv6Address", host, e);
        }
        return new Host(new DomainName(host));
    }
}

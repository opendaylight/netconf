/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.api;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

/**
 * Created by adetalhouet on 2016-11-03.
 */
public interface NetconfConnectorFactory {

    /**
     * Create a new netconf connector with default values.
     *
     * <p>
     * This method will create a {@link Node} and a {@link NetconfNode}
     * that will be added as an augmentation to the {@link Node}.
     * Afterward, that {@link Node} will be written in the MDSAL datastore under the {@link NetconfTopology}.
     * Listeners of that subtree located within network-topology bundle will setup the session.
     *
     * @param dataBroker Instance of the {@link DataBroker}
     * @param instanceName The name of the node
     * @param address The address
     * @param port The port
     * @param username The username of the netconf session
     * @param password The password of the netconf session
     * @param tcpOnly Whether to create a TCP or SSH session
     * @param protocol Which kind of session to be created, TCP, SSH or TLS
     * @param reconnectOnSchemaChange Whether to enable ietf-netconf-monitoring and register the NETCONF stream.
     * @return The created {@link Node}
     */
    Node newInstance(DataBroker dataBroker,
                     String instanceName,
                     String address,
                     Integer port,
                     String username,
                     String password,
                     Boolean tcpOnly,
                     String protocol,
                     Boolean reconnectOnSchemaChange);
}

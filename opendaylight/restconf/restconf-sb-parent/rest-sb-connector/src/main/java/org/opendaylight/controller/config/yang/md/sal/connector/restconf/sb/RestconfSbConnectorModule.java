/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.md.sal.connector.restconf.sb;

import org.opendaylight.restconfsb.RestconfDeviceProvider;
import org.opendaylight.restconfsb.communicator.impl.sender.NettyHttpClientProvider;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.restconfsb.communicator.impl.sender.TrustStore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

public class RestconfSbConnectorModule extends org.opendaylight.controller.config.yang.md.sal.connector.restconf.sb.AbstractRestconfSbConnectorModule {
    public RestconfSbConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public RestconfSbConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final org.opendaylight.controller.config.yang.md.sal.connector.restconf.sb.RestconfSbConnectorModule oldModule, final java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final RestconfNode restconfNode = new RestconfNodeBuilder()
                .setAddress(getAddress())
                .setPort(getPort())
                .setUsername(getUsername())
                .setPassword(getPassword())
                .setRequestTimeout(getRequestTimeout())
                .build();
        final Node node = new NodeBuilder()
                .setNodeId(new NodeId(getId()))
                .addAugmentation(RestconfNode.class, restconfNode)
                .build();

        final TrustStore trustStore = new TrustStore(getTruststorePath(), getTruststorePassword(), getTruststoreType(),
                getTruststorePathType());

        final SenderFactory senderFactory = new SenderFactory(new NettyHttpClientProvider(trustStore), getReconnectStreamsFail());
        final RestconfDeviceProvider provider = new RestconfDeviceProvider(node, getProcessingExecutorDependency(), getReconnectExecutorDependency(),
                senderFactory);
        getDomRegistryDependency().registerProvider(provider);
        return provider;
    }

}

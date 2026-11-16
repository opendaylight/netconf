/*
 * Copyright (c) 2026 Smartoptics and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;

/**
 * A master that stops being master while its device stays configured leaves the device's operational data to the next
 * master. Only when the device's configuration is deleted does it delete that data.
 */
@ExtendWith(MockitoExtension.class)
class NetconfTopologyContextTest {
    @Mock
    private NetconfNodeContext nodeContext;
    @Mock
    private BaseNetconfSchemaProvider baseSchemaProvider;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private SchemaResourceManager schemaManager;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private NetconfClientConfigurationBuilderFactory builderFactory;
    @Mock
    private DeviceActionFactory deviceActionFactory;

    private NetconfTopologyContext context;

    @BeforeEach
    void beforeEach() {
        final var setup = NetconfTopologySetup.builder()
            .setBaseSchemaProvider(baseSchemaProvider)
            .setDataBroker(dataBroker)
            .setTopologyId("topology-netconf")
            .setNode(new NodeBuilder()
                .setNodeId(new NodeId("device"))
                .addAugmentation(new NetconfNodeAugmentBuilder()
                    .setNetconfNode(new NetconfNodeBuilder()
                        .setHost(new Host(new IpAddress(new Ipv4Address("127.0.0.1"))))
                        .setPort(new PortNumber(Uint16.valueOf(17830)))
                        .build())
                    .build())
                .build())
            .build();
        context = new NetconfTopologyContext(schemaManager, mountPointService, builderFactory, deviceActionFactory,
                Duration.ofSeconds(10), new ServiceGroupIdentifier("device"), setup) {
            @Override
            protected NetconfNodeContext getTopologySingleton() {
                return nodeContext;
            }
        };
        context.instantiateServiceInstance();
    }

    @Test
    void handoverLeavesOperationalData() {
        context.closeServiceInstance();

        final var order = inOrder(nodeContext);
        order.verify(nodeContext).releaseOperationalData();
        order.verify(nodeContext).becomeTopologyFollower(any());
    }

    @Test
    void shutdownLeavesOperationalData() {
        context.close();

        final var order = inOrder(nodeContext);
        order.verify(nodeContext).releaseOperationalData();
        order.verify(nodeContext).close();
    }

    @Test
    void removalDeletesOperationalData() {
        context.markRemoved();
        context.closeServiceInstance();
        context.close();

        verify(nodeContext).becomeTopologyFollower(any());
        verify(nodeContext).close();
        verify(nodeContext, never()).releaseOperationalData();
    }
}

/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

// Non-final for mocking
class CallHomeTopology extends AbstractNetconfTopology {
    CallHomeTopology(final String topologyId, final NetconfClientFactory clientFactory, final NetconfTimer timer,
            final NetconfTopologySchemaAssembler schemaAssembler, final SchemaResourceManager schemaRepositoryProvider,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final NetconfClientConfigurationBuilderFactory builderFactory, final BaseNetconfSchemas baseSchemas,
            final DeviceActionFactory deviceActionFactory) {
        super(topologyId, clientFactory, timer, schemaAssembler, schemaRepositoryProvider, dataBroker,
            mountPointService, builderFactory, deviceActionFactory, baseSchemas);
    }

    void disableNode(final NodeId nodeId) {
        deleteNode(nodeId);
    }

    void enableNode(final Node node) {
        ensureNode(node);
    }
}

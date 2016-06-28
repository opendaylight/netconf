/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.it.provider.datawriter;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import com.google.common.util.concurrent.*;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107.InterfaceActive;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107.InterfaceConfigurations;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfiguration;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfigurationBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfigurationKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.xr.types.rev150119.InterfaceName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.BenchmarkTestInput;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public abstract class AbstractNetconfWriter extends AbstractDataWriter {
    DataBroker xrNodeBroker = null;
    Set<NodeId> nodeIdSet;
    Map<NodeId, List<InterfaceName>> nodeIfList = new HashMap<>();
    public static final InstanceIdentifier<Topology> NETCONF_TOPO_IID = InstanceIdentifier
            .create(NetworkTopology.class).child(
                    Topology.class, new TopologyKey(new TopologyId(TopologyNetconf.QNAME
                            .getLocalName())));
    InstanceIdentifier<InterfaceConfigurations> netconfIid = InstanceIdentifier.create(InterfaceConfigurations.class);
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfWriter.class);


    public AbstractNetconfWriter(BenchmarkTestInput input, DataBroker xrNodeBroker, Set nodeidset, Map<NodeId, List<InterfaceName>> nodeiflist) {
        super(input);
        this.xrNodeBroker = xrNodeBroker;
        this.nodeIdSet = nodeidset;
        this.nodeIfList = nodeiflist;
    }

    public void configInterface() {
        WriteTransaction xrNodeWriteTx = null;
        for (int i = 1; i <= input.getLoop(); i++) {
            xrNodeWriteTx = xrNodeBroker.newWriteOnlyTransaction();

            InterfaceName subIfName = new InterfaceName(DTXITConstants.INTERFACE_NAME_PREFIX + i);
            KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> specificInterfaceCfgIid
                    = netconfIid.child(InterfaceConfiguration.class, new InterfaceConfigurationKey(
                    new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE), subIfName));

            InterfaceConfigurationBuilder interfaceConfigurationBuilder = new InterfaceConfigurationBuilder();
            interfaceConfigurationBuilder.setInterfaceName(subIfName);
            interfaceConfigurationBuilder.setActive(new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE));
            InterfaceConfiguration config = interfaceConfigurationBuilder.build();

            xrNodeWriteTx.put(LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config);
            CheckedFuture<Void, TransactionCommitFailedException> submitFut = xrNodeWriteTx.submit();
            try {
                submitFut.checkedGet();
            } catch (TransactionCommitFailedException e) {
                LOG.trace("Can't build interface {}", subIfName.toString());
            }
        }
    }
}

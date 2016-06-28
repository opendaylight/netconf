/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.it.provider.datawriter;

import com.google.common.util.concurrent.*;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTx;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.api.DTxProvider;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107.InterfaceActive;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfiguration;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfigurationBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfigurationKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.xr.types.rev150119.InterfaceName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.BenchmarkTestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.OperationType;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import java.util.Map;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Data write using distributed-tx API to synchronously write to NetConf device
 */
public class DtxNetConfSyncWriter extends AbstractNetconfWriter {
    private DTx dtx;
    private DTxProvider dTxProvider;

    public DtxNetConfSyncWriter(BenchmarkTestInput input, org.opendaylight.controller.md.sal.binding.api.DataBroker db, DTxProvider dtxProvider, Set nodeidset, Map<NodeId, List<InterfaceName>> nodeiflist) {
        super(input,db,nodeidset,nodeiflist);
        this.dTxProvider=dtxProvider;
    }

    /**
     * Synchronously write to NetConf device with distributed-tx API
     */
    @Override
    public void writeData() {
        long putsPerTx = input.getPutsPerTx();
        List<NodeId> nodeIdList = new ArrayList(nodeIdSet);
        Set<InstanceIdentifier<?>> txIidSet = new HashSet<>();
        NodeId n =nodeIdList.get(0);
        InstanceIdentifier msNodeId = NETCONF_TOPO_IID.child(Node.class, new NodeKey(n));
        int counter = 0;
        InterfaceName ifName = nodeIfList.get(n).get(0);

        if (input.getOperation() == OperationType.DELETE) {
            //Build subInterfaces for delete operation
            configInterface();
        }

        txIidSet.add(msNodeId);
        dtx=dTxProvider.newTx(txIidSet);
        startTime = System.nanoTime();

        for(int i = 1; i<= input.getLoop(); i++){
            KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> specificInterfaceCfgIid
                    = netconfIid.child(InterfaceConfiguration.class, new InterfaceConfigurationKey(
                    new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE), ifName));
            InterfaceConfigurationBuilder interfaceConfigurationBuilder = new InterfaceConfigurationBuilder();
            interfaceConfigurationBuilder.setInterfaceName(ifName);

            interfaceConfigurationBuilder.setDescription(DTXITConstants.TEST_DESCRIPTION + input.getOperation() + i);
            interfaceConfigurationBuilder.setActive(new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE));
            InterfaceConfiguration config = interfaceConfigurationBuilder.build();

            CheckedFuture<Void, DTxException> writeFuture = null;
            if (input.getOperation() == OperationType.PUT) {
                writeFuture = dtx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config, msNodeId);
            } else if (input.getOperation() == OperationType.MERGE) {
                writeFuture = dtx.mergeAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config, msNodeId);
            } else {
                InterfaceName subIfName = new InterfaceName(DTXITConstants.INTERFACE_NAME_PREFIX + i);
                KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> subSpecificInterfaceCfgIid
                        = netconfIid.child(InterfaceConfiguration.class, new InterfaceConfigurationKey(
                        new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE), subIfName));
                writeFuture = dtx.deleteAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        LogicalDatastoreType.CONFIGURATION, subSpecificInterfaceCfgIid, msNodeId);
            }
            counter++;

            try {
                writeFuture.checkedGet();
            } catch (Exception e) {
                txError++;
                counter=0;
                dtx=dTxProvider.newTx(txIidSet);
                continue;
            }

            if (counter == putsPerTx) {
                CheckedFuture<Void, TransactionCommitFailedException> submitFuture = dtx.submit();
                try {
                    submitFuture.checkedGet();
                    txSucceed++;
                } catch (TransactionCommitFailedException e) {
                    txError++;
                }
                counter = 0;
                dtx= this.dTxProvider.newTx(txIidSet);
            }
        }

        CheckedFuture<Void, TransactionCommitFailedException> restSubmitFuture = dtx.submit();
        try {
            restSubmitFuture.checkedGet();
            txSucceed++;
        }catch (Exception e) {
            txError++;
        }
        endTime = System.nanoTime();
    }
}







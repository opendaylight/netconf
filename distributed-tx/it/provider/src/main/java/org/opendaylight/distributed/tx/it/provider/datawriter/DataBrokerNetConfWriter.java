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
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfiguration;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfigurationBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfigurationKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.xr.types.rev150119.InterfaceName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.BenchmarkTestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.OperationType;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * Data write using MD-SAL NetConf transaction provider API to write to NetConf device
 */
public class DataBrokerNetConfWriter extends AbstractNetconfWriter {

    public DataBrokerNetConfWriter(BenchmarkTestInput input, DataBroker db, Set nodeIdSet, Map<NodeId, List<InterfaceName>> nodeIfList) {
        super(input, db, nodeIdSet, nodeIfList);
    }

    /**
     * Write configuration to NetConf devices with MD-SAL NetConf transaction provider API
     */
    @Override
    public void writeData() {
        int putsPerTx = input.getPutsPerTx();
        int counter = 0;
        List<NodeId> nodeIdList = new ArrayList(nodeIdSet);
        NodeId nodeId = nodeIdList.get(0);
        InterfaceName ifName = nodeIfList.get(nodeId).get(0) ;

        if (input.getOperation() == OperationType.DELETE) {
            //Build subInterfaces for delete operation
            configInterface();
        }

        WriteTransaction xrNodeWriteTx = xrNodeBroker.newWriteOnlyTransaction();

        startTime = System.nanoTime();
        for (int i = 1; i<= input.getLoop(); i++){
            KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> specificInterfaceCfgIid
                    = netconfIid.child(InterfaceConfiguration.class, new InterfaceConfigurationKey(
                    new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE), ifName));

            InterfaceConfigurationBuilder interfaceConfigurationBuilder = new InterfaceConfigurationBuilder();
            interfaceConfigurationBuilder.setInterfaceName(ifName);
            interfaceConfigurationBuilder.setDescription(DTXITConstants.TEST_DESCRIPTION + input.getOperation() + i);
            interfaceConfigurationBuilder.setActive(new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE));
            InterfaceConfiguration config = interfaceConfigurationBuilder.build();

            if (input.getOperation() == OperationType.PUT) {
                //Put configuration to the same interface
                xrNodeWriteTx.put(LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config);
            } else if (input.getOperation() == OperationType.MERGE) {
                //Merge configuration to the same interface
                xrNodeWriteTx.merge(LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config);
            } else {
                //Delete subInterfaces
                InterfaceName subIfName = new InterfaceName(DTXITConstants.INTERFACE_NAME_PREFIX + i);
                KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> subSpecificInterfaceCfgIid
                        = netconfIid.child(InterfaceConfiguration.class, new InterfaceConfigurationKey(new InterfaceActive("act"), subIfName));
                xrNodeWriteTx.delete(LogicalDatastoreType.CONFIGURATION, subSpecificInterfaceCfgIid);
            }
            counter++;

            if (counter == putsPerTx) {
                CheckedFuture<Void, TransactionCommitFailedException> submitFut = xrNodeWriteTx.submit();
                try {
                    submitFut.checkedGet();
                    txSucceed++;
                }catch (Exception e){
                    txError++;
                }
                counter = 0;
                xrNodeWriteTx=xrNodeBroker.newReadWriteTransaction();
            }
        }

        CheckedFuture<Void, TransactionCommitFailedException> restSubmitFuture = xrNodeWriteTx.submit();
        try
        {
            restSubmitFuture.checkedGet();
            txSucceed++;
        }catch (Exception e) {
            txError++;
        }
        endTime = System.nanoTime();
    }
}







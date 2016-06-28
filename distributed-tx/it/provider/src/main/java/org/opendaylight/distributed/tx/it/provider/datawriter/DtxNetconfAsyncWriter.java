/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.it.provider.datawriter;

import com.google.common.base.Function;
import com.google.common.util.concurrent.*;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
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
import javax.annotation.Nullable;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * Data write using distributed-tx API to asynchronously write to NetConf devices
 */
public class DtxNetconfAsyncWriter extends AbstractNetconfWriter {
    private DTx dtx;
    private DTxProvider dTxProvider;

    public DtxNetconfAsyncWriter(BenchmarkTestInput input, DataBroker db, DTxProvider dTxProvider, Set nodeidset, Map<NodeId, List<InterfaceName>> nodeiflist) {
        super(input,db, nodeidset, nodeiflist);
        this.dTxProvider = dTxProvider;
    }

    /**
     * Asynchronously write configuration to NetConf device with distributed-tx API
     */
    @Override
    public void writeData() {
        int putsPerTx = input.getPutsPerTx();
        int counter = 0;
        List<ListenableFuture<Void>> putFutures = new ArrayList<ListenableFuture<Void>>(putsPerTx);
        List<NodeId> nodeIdList = new ArrayList(this.nodeIdSet);
        Set<InstanceIdentifier<?>> txIidSet = new HashSet<>();
        NodeId nodeId = nodeIdList.get(0);
        InstanceIdentifier msNodeId = NETCONF_TOPO_IID.child(Node.class, new NodeKey(nodeId));
        InterfaceName ifName = nodeIfList.get(nodeId).get(0);

        if (input.getOperation() == OperationType.DELETE) {
            //Build subInterfaces for delete operation
            configInterface();
        }

        txIidSet.add(msNodeId);
        dtx = dTxProvider.newTx(txIidSet);
        startTime = System.nanoTime();
        for(int i=1;i<=input.getLoop();i++){
            KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> specificInterfaceCfgIid
                    = netconfIid.child(InterfaceConfiguration.class, new InterfaceConfigurationKey(new InterfaceActive(
                    DTXITConstants.INTERFACE_ACTIVE), ifName));

            InterfaceConfigurationBuilder interfaceConfigurationBuilder = new InterfaceConfigurationBuilder();
            interfaceConfigurationBuilder.setInterfaceName(ifName);
            interfaceConfigurationBuilder.setDescription(DTXITConstants.TEST_DESCRIPTION + input.getOperation() + i);
            interfaceConfigurationBuilder.setActive(new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE));
            InterfaceConfiguration config = interfaceConfigurationBuilder.build();

            CheckedFuture<Void, DTxException> writeFuture = null;
            if (input.getOperation() == OperationType.PUT) {
                //Put configuration to the same interface
                writeFuture = dtx.putAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config, msNodeId);
            } else if (input.getOperation() == OperationType.MERGE) {
                //Merge configuration to the same interface
                writeFuture = dtx.mergeAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        LogicalDatastoreType.CONFIGURATION, specificInterfaceCfgIid, config, msNodeId);
            } else {
                //Delete subInterfaces
                InterfaceName subIfName = new InterfaceName(DTXITConstants.INTERFACE_NAME_PREFIX + i);
                KeyedInstanceIdentifier<InterfaceConfiguration, InterfaceConfigurationKey> subSpecificInterfaceCfgIid
                        = netconfIid.child(InterfaceConfiguration.class, new InterfaceConfigurationKey(
                        new InterfaceActive(DTXITConstants.INTERFACE_ACTIVE), subIfName));
                writeFuture = dtx.deleteAndRollbackOnFailure(DTXLogicalTXProviderType.NETCONF_TX_PROVIDER,
                        LogicalDatastoreType.CONFIGURATION, subSpecificInterfaceCfgIid, msNodeId);
            }
            putFutures.add(writeFuture);
            counter++;

            if (counter == putsPerTx) {
                ListenableFuture<Void> aggregatePutFuture = Futures.transform(Futures.allAsList(putFutures), new Function<List<Void>, Void>() {
                    @Nullable
                    @Override
                    public Void apply(@Nullable List<Void> voids) {
                        return null;
                    }
                });
                try {
                    aggregatePutFuture.get();
                    CheckedFuture<Void, TransactionCommitFailedException> submitFuture = dtx.submit();
                    try {
                        submitFuture.checkedGet();
                        txSucceed++;
                    } catch (TransactionCommitFailedException e) {
                        txError++;
                    }
                } catch (Exception e) {
                    txError++;
                    dtx.cancel();
                }

                counter = 0;
                dtx = dTxProvider.newTx(txIidSet);
                putFutures = new ArrayList<ListenableFuture<Void>>((int) putsPerTx);
            }
        }

        ListenableFuture<Void> aggregatePutFuture = Futures.transform(Futures.allAsList(putFutures), new Function<List<Void>, Void>() {
            @Nullable
            @Override
            public Void apply(@Nullable List<Void> voids) {
                return null;
            }
        });

        try{
            aggregatePutFuture.get();
            CheckedFuture<Void, TransactionCommitFailedException> restSubmitFuture = dtx.submit();
            try {
                restSubmitFuture.checkedGet();
                txSucceed++;
            }catch (Exception e) {
                txError ++;
            }
        }catch (Exception e) {
            txError ++;
        }
        endTime = System.nanoTime();
    }
}
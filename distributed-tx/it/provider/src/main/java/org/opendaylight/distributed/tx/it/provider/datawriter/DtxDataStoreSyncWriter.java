/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.it.provider.datawriter;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.distributed.tx.api.DTx;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.api.DTxProvider;
import org.opendaylight.distributed.tx.it.provider.DataStoreListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.BenchmarkTestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.DatastoreTestData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.OperationType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.OuterList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.outer.list.InnerList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data write using distributed-tx API to synchronously write to datastore
 */
public class DtxDataStoreSyncWriter extends AbstractDataWriter {
    private DTx dtx;
    private DTxProvider dTxProvider;
    private Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap;
    private DataBroker dataBroker;
    private DataStoreListBuilder dataStoreListBuilder;

    public DtxDataStoreSyncWriter(BenchmarkTestInput input, DTxProvider dTxProvider, DataBroker dataBroker, Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap) {
        super(input);
        this.dTxProvider = dTxProvider;
        this.nodesMap = nodesMap;
        this.dataBroker = dataBroker;
        dataStoreListBuilder = new DataStoreListBuilder(dataBroker, input.getOuterList(), input.getInnerList());
    }

    /**
     * Synchronously write to datastore with distributed-tx API
     */
    @Override
    public void writeData() {
        int putsPerTx = input.getPutsPerTx();
        int counter = 0;
        List<OuterList> outerLists = dataStoreListBuilder.buildOuterList();

        if (input.getOperation() == OperationType.DELETE) {
            dataStoreListBuilder.buildTestInnerList();
        }

        InstanceIdentifier<DatastoreTestData> nodeId = InstanceIdentifier.create(DatastoreTestData.class);

        dtx = dTxProvider.newTx(nodesMap);
        startTime = System.nanoTime();
        for ( OuterList outerList : outerLists ) {
            for (InnerList innerList : outerList.getInnerList() ) {
                InstanceIdentifier<InnerList> innerIid = InstanceIdentifier.create(DatastoreTestData.class)
                        .child(OuterList.class, outerList.getKey())
                        .child(InnerList.class, innerList.getKey());

                CheckedFuture<Void, DTxException> writeFuture ;
                if (input.getOperation() == OperationType.PUT) {
                    writeFuture = dtx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.CONFIGURATION, innerIid, innerList, nodeId);
                }else if (input.getOperation() == OperationType.MERGE){
                    writeFuture = dtx.mergeAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.CONFIGURATION, innerIid, innerList, nodeId);
                }else{
                    writeFuture = dtx.deleteAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.CONFIGURATION, innerIid, nodeId);
                }
                counter++;

                try{
                    writeFuture.checkedGet();
                }catch (Exception e) {
                    txError++;
                    counter = 0;
                    dtx = dTxProvider.newTx(nodesMap);
                    continue;
                }

                if (counter == putsPerTx) {
                    CheckedFuture<Void, TransactionCommitFailedException> submitFuture = dtx.submit();
                    try{
                        submitFuture.checkedGet();
                        txSucceed++;
                    }catch (TransactionCommitFailedException e) {
                        txError++;
                    }
                    counter = 0;
                    dtx = dTxProvider.newTx(nodesMap);
                }
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

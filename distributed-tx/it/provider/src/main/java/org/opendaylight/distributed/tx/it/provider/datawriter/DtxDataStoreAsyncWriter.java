/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.it.provider.datawriter;

import com.google.common.base.Function;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data write using distributed-tx API to asynchronously write to datastore
 */
public class DtxDataStoreAsyncWriter extends AbstractDataWriter {
    private DTx dtx;
    private DTxProvider dTxProvider;
    private Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap;
    private DataBroker dataBroker;
    private DataStoreListBuilder dataStoreListBuilder;

    public DtxDataStoreAsyncWriter(BenchmarkTestInput input, DTxProvider dTxProvider, DataBroker dataBroker, Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodesMap)
    {
        super(input);
        this.dTxProvider = dTxProvider;
        this.nodesMap = nodesMap;
        this.dataBroker = dataBroker;
        dataStoreListBuilder = new DataStoreListBuilder(dataBroker, input.getOuterList(), input.getInnerList());
    }

    /**
     * Asynchronously write to datastore with distributed-tx API
     */
    @Override
    public void writeData() {
        int putsPerTx = input.getPutsPerTx();
        int counter = 0;

        InstanceIdentifier<DatastoreTestData> nodeId = InstanceIdentifier.create(DatastoreTestData.class);
        List<ListenableFuture<Void>> putFutures = new ArrayList<ListenableFuture<Void>>((int) putsPerTx);
        List<OuterList> outerLists = dataStoreListBuilder.buildOuterList();

        if (input.getOperation() == OperationType.DELETE) {
            dataStoreListBuilder.buildTestInnerList();
        }

        dtx = dTxProvider.newTx(nodesMap);
        startTime = System.nanoTime();
        for ( OuterList outerList : outerLists ) {
            for (InnerList innerList : outerList.getInnerList() ) {
                InstanceIdentifier<InnerList> innerIid = InstanceIdentifier.create(DatastoreTestData.class)
                        .child(OuterList.class, outerList.getKey())
                        .child(InnerList.class, innerList.getKey());

                CheckedFuture<Void, DTxException> writeFuture;
                if (input.getOperation() == OperationType.PUT) {
                    writeFuture = dtx.putAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.CONFIGURATION, innerIid, innerList, nodeId);
                }else if (input.getOperation() == OperationType.MERGE){
                    writeFuture = dtx.mergeAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.CONFIGURATION, innerIid, innerList, nodeId);
                }else{
                    writeFuture = dtx.deleteAndRollbackOnFailure(DTXLogicalTXProviderType.DATASTORE_TX_PROVIDER, LogicalDatastoreType.CONFIGURATION, innerIid, nodeId);
                }
                putFutures.add(writeFuture);
                counter++;

                if (counter == putsPerTx) {
                    //Aggregate all the put futures into a listenable future which can ensure all asynchronous writes has been finished
                    ListenableFuture<Void> aggregatePutFuture = Futures.transform(Futures.allAsList(putFutures), new Function<List<Void>, Void>() {
                        @Nullable
                        @Override
                        public Void apply(@Nullable List<Void> voids) {
                            return null;
                        }
                    });

                    try{
                        aggregatePutFuture.get();
                        CheckedFuture<Void, TransactionCommitFailedException> submitFuture = dtx.submit();
                        try{
                            submitFuture.checkedGet();
                            txSucceed++;
                        }catch (TransactionCommitFailedException e) {
                            txError++;
                        }
                    }catch (Exception e) {
                        txError++;
                        dtx.cancel();
                    }

                    counter = 0;
                    dtx = dTxProvider.newTx(nodesMap);
                    putFutures = new ArrayList<ListenableFuture<Void>>(putsPerTx);
                }
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
                txError++;
            }
        }catch (Exception e) {
            txError++;
        }
        endTime = System.nanoTime();
    }
}

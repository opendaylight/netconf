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
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.it.provider.DataStoreListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.BenchmarkTestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.DatastoreTestData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.OperationType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.OuterList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.outer.list.InnerList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import java.util.List;

/**
 * Data write using MD-SAL datastore transaction provider API to write to datastore
 */
public class DataBrokerDataStoreWriter extends AbstractDataWriter{
    private DataBroker dataBroker;
    private DataStoreListBuilder dataStoreListBuilder;

    public DataBrokerDataStoreWriter(BenchmarkTestInput input, DataBroker db) {
        super(input);
        this.dataBroker = db;
        dataStoreListBuilder = new DataStoreListBuilder(db, input.getOuterList(), input.getInnerList());
    }

    /**
     * Write to datastore with MD-SAL datastore transaction provider API
     */
    @Override
    public void writeData() {
        int counter = 0;
        int putsPerTx = input.getPutsPerTx();
        List<OuterList> outerLists = dataStoreListBuilder.buildOuterList();

        if (input.getOperation() == OperationType.DELETE) {
            dataStoreListBuilder.buildTestInnerList();
        }

        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();

        startTime = System.nanoTime();
        for ( OuterList outerList : outerLists ) {
            for (InnerList innerList : outerList.getInnerList()) {
                InstanceIdentifier<InnerList> innerIid = InstanceIdentifier.create(DatastoreTestData.class)
                        .child(OuterList.class, outerList.getKey())
                        .child(InnerList.class, innerList.getKey());
                if (input.getOperation() == OperationType.PUT) {
                    tx.put(LogicalDatastoreType.CONFIGURATION, innerIid, innerList);
                }else if (input.getOperation() == OperationType.MERGE){
                    tx.merge(LogicalDatastoreType.CONFIGURATION, innerIid, innerList);
                }else {
                    tx.delete(LogicalDatastoreType.CONFIGURATION, innerIid);
                }
                counter++;

                if (counter == putsPerTx) {
                    CheckedFuture<Void, TransactionCommitFailedException> submitFut = tx.submit();
                    try{
                        submitFut.checkedGet();
                        txSucceed++;
                    }catch (Exception e) {
                        txError++;
                    }
                    counter = 0;
                    tx = dataBroker.newWriteOnlyTransaction();
                }
            }
        }
        CheckedFuture<Void, TransactionCommitFailedException> restSubmitFuture = tx.submit();

        try {
            restSubmitFuture.checkedGet();
            txSucceed++;
        }catch (Exception e) {
            txError++;
        }
        endTime = System.nanoTime();
    }
}

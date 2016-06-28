/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.it.provider;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.DatastoreTestData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.OuterList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.OuterListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.OuterListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.outer.list.InnerList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.outer.list.InnerListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.datastore.test.data.outer.list.InnerListKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import java.util.ArrayList;
import java.util.List;

public class DataStoreListBuilder {
    DataBroker dataBroker;
    int outerElements;
    int innerElements;

    public DataStoreListBuilder(DataBroker dataBroker, int outerElements, int innerElements) {
        this.dataBroker = dataBroker;
        this.outerElements = outerElements;
        this.innerElements = innerElements;
    }
    /**
     * This method builds test data for delete operation
     */
    public boolean buildTestInnerList() {
        List<OuterList> outerLists = buildOuterList();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        for ( OuterList outerList : outerLists) {
            InstanceIdentifier<OuterList> outerListIid = InstanceIdentifier.create(DatastoreTestData.class)
                    .child(OuterList.class, outerList.getKey());

            transaction.put(LogicalDatastoreType.CONFIGURATION, outerListIid, outerList);
            CheckedFuture<Void, TransactionCommitFailedException> submitFuture = transaction.submit();

            try{
                submitFuture.checkedGet();
            }catch (Exception e) {
                return false;
            }
            transaction = dataBroker.newWriteOnlyTransaction();
        }
        return true;
    }

    public List<OuterList> buildOuterList() {
        List<OuterList> outerList = new ArrayList<OuterList>(outerElements);
        for (int j = 0; j < outerElements; j++) {
            outerList.add(new OuterListBuilder()
                    .setId( j )
                    .setInnerList(buildInnerList(j, innerElements))
                    .setKey(new OuterListKey( j ))
                    .build());
        }
        return outerList;
    }

    private List<InnerList> buildInnerList(int index, int elements ) {
        List<InnerList> innerList = new ArrayList<InnerList>( elements );
        final String itemStr = "Item-" + String.valueOf(index) + "-";
        for( int i = 0; i < elements; i++ ) {
            innerList.add(new InnerListBuilder()
                    .setKey( new InnerListKey( i ) )
                    .setName(i)
                    .setValue( itemStr + String.valueOf( i ) )
                    .build());
        }
        return innerList;
    }
}

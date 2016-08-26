/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class PutDataTransactionUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";
    private static final ControllerContext controllerContext = ControllerContext.getInstance();

    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private DOMDataReadWriteTransaction readWrite;
    @Mock
    private DOMDataReadOnlyTransaction read;
    @Mock
    private DOMDataWriteTransaction write;

    private NormalizedNodeContext payload;
    private SchemaContextRef refSchemaCtx;
    private LeafNode buildLeaf;
    private ContainerNode buildBaseCont;
    private MapEntryNode buildList;
    private InstanceIdentifierContext<? extends SchemaNode> iidContext;
    private SchemaContext schema;
    private DataSchemaNode schemaNode;
    private YangInstanceIdentifier iid;
    private DataSchemaNode schemaNode2;
    private YangInstanceIdentifier iid2;
    private DataSchemaNode schemaNode3;
    private YangInstanceIdentifier iid3;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        refSchemaCtx = new SchemaContextRef(TestRestconfUtils.loadSchemaContext(PATH_FOR_NEW_SCHEMA_CONTEXT));
        controllerContext.setGlobalSchema(refSchemaCtx.get());
        schema = controllerContext.getGlobalSchema();

        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerQname = QName.create(baseQName, "player");
        final QName leafQname = QName.create(baseQName, "gap");
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");
        final QName listsLeafQname = QName.create(baseQName, "description");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQname, "name of band");

        iid = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerQname)
                .node(leafQname)
                .build();
        schemaNode = DataSchemaContextTree.from(schema).getChild(iid).getDataSchemaNode();

        iid2 = YangInstanceIdentifier.builder()
                .node(baseQName)
                .build();
        schemaNode2 = DataSchemaContextTree.from(schema).getChild(iid2).getDataSchemaNode();

        iid3 = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(listQname)
                .node(nodeWithKey)
                .build();
        schemaNode3 = DataSchemaContextTree.from(schema).getChild(iid3).getDataSchemaNode();

        buildLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(leafQname))
                .withValue(0.2)
                .build();
        final ContainerNode buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerQname))
                .withChild(buildLeaf)
                .build();
        buildBaseCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(baseQName))
                .withChild(buildPlayerCont)
                .build();
        final LeafNode<Object> content = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of band")
                .build();
        final LeafNode<Object> content2 = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "description")))
                .withValue("band description")
                .build();
        buildList = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content)
                .withChild(content2)
                .build();
    }

    @Test
    public void testValidInputData() throws Exception {
        iidContext = new InstanceIdentifierContext<>(iid, schemaNode, null, schema);
        payload = new NormalizedNodeContext(iidContext, buildLeaf);
        PutDataTransactionUtil.validInputData(iidContext.getSchemaNode(), payload);
    }

    @Test
    public void testValidTopLevelNodeName() throws Exception {
        iidContext = new InstanceIdentifierContext<>(iid, schemaNode, null, schema);
        payload = new NormalizedNodeContext(iidContext, buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(iidContext.getInstanceIdentifier(), payload);

        iidContext = new InstanceIdentifierContext<>(iid2, schemaNode2, null, schema);
        payload = new NormalizedNodeContext(iidContext, buildBaseCont);
        PutDataTransactionUtil.validTopLevelNodeName(iidContext.getInstanceIdentifier(), payload);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testValidTopLevelNodeNamePathEmpty() throws Exception {
        iidContext = new InstanceIdentifierContext<>(iid, schemaNode, null, schema);
        payload = new NormalizedNodeContext(iidContext, buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(YangInstanceIdentifier.EMPTY, payload);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testValidTopLevelNodeNameWrongTopIdentifier() throws Exception {
        iidContext = new InstanceIdentifierContext<>(iid, schemaNode, null, schema);
        payload = new NormalizedNodeContext(iidContext, buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(iid.getAncestor(1), payload);
    }

    @Test
    public void testValidateListKeysEqualityInPayloadAndUri() throws Exception {
        iidContext = new InstanceIdentifierContext<>(iid3, schemaNode3, null, schema);
        payload = new NormalizedNodeContext(iidContext, buildList);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);
    }

    @Test
    public void testPutContainerData() throws Exception {
        iidContext = new InstanceIdentifierContext<>(iid2, schemaNode2, null, schema);
        payload = new NormalizedNodeContext(iidContext, buildBaseCont);

        doReturn(readWrite).when(transactionChain).newReadWriteTransaction();
        doReturn(read).when(transactionChain).newReadOnlyTransaction();
        doReturn(write).when(transactionChain).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION, iid2);
        doNothing().when(write).put(LogicalDatastoreType.CONFIGURATION, payload.getInstanceIdentifierContext().getInstanceIdentifier(),
                payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(write).submit();

        PutDataTransactionUtil.putData(payload, refSchemaCtx,
                new TransactionVarsWrapper(payload.getInstanceIdentifierContext(), null, transactionChain));
        verify(read).read(LogicalDatastoreType.CONFIGURATION, payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(write).put(LogicalDatastoreType.CONFIGURATION, payload.getInstanceIdentifierContext().getInstanceIdentifier(),
                payload.getData());
    }

    @Test
    public void testPutleafData() throws Exception {
        iidContext = new InstanceIdentifierContext<>(iid, schemaNode, null, schema);
        payload = new NormalizedNodeContext(iidContext, buildLeaf);

        doReturn(readWrite).when(transactionChain).newReadWriteTransaction();
        doReturn(read).when(transactionChain).newReadOnlyTransaction();
        doReturn(write).when(transactionChain).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION, iid);
        doNothing().when(write).put(LogicalDatastoreType.CONFIGURATION, payload.getInstanceIdentifierContext().getInstanceIdentifier(),
                payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(write).submit();

        PutDataTransactionUtil.putData(payload, refSchemaCtx,
                new TransactionVarsWrapper(payload.getInstanceIdentifierContext(), null, transactionChain));
        verify(read).read(LogicalDatastoreType.CONFIGURATION, payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(write).put(LogicalDatastoreType.CONFIGURATION, payload.getInstanceIdentifierContext().getInstanceIdentifier(),
                payload.getData());
    }

    /*
     *Prepared for put transaction test of list data
     *
     */
    @Ignore
    @Test
    public void testPutListData() throws Exception {
        payload = new NormalizedNodeContext(iidContext, buildBaseCont);
        iidContext = new InstanceIdentifierContext<>(iid, schemaNode, null, schema);

        doReturn(readWrite).when(transactionChain).newReadWriteTransaction();
        doReturn(read).when(transactionChain).newReadOnlyTransaction();
        doReturn(write).when(transactionChain).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION, iid);
        doNothing().when(write).put(LogicalDatastoreType.CONFIGURATION, payload.getInstanceIdentifierContext().getInstanceIdentifier(),
                payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(write).submit();
    }

    /*
     *Prepared for put transaction test of data that need to be merged with some data that already exist in datastore
     *
     */

    @Ignore
    @Test
    public void testPutDataNeedToMerge() throws Exception {
        payload = new NormalizedNodeContext(iidContext, buildBaseCont);
        iidContext = new InstanceIdentifierContext<>(iid, schemaNode, null, schema);

        doReturn(readWrite).when(transactionChain).newReadWriteTransaction();
        doReturn(read).when(transactionChain).newReadOnlyTransaction();
        doReturn(write).when(transactionChain).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION, iid);
        doNothing().when(write).put(LogicalDatastoreType.CONFIGURATION, payload.getInstanceIdentifierContext().getInstanceIdentifier(),
                payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(write).submit();
    }

}


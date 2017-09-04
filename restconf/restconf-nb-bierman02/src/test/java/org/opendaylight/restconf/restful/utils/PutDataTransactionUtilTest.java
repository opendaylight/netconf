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
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
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
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class PutDataTransactionUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";

    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private DOMDataReadWriteTransaction readWrite;
    @Mock
    private DOMDataReadOnlyTransaction read;
    @Mock
    private DOMDataWriteTransaction write;


    private SchemaContextRef refSchemaCtx;
    private LeafNode buildLeaf;
    private ContainerNode buildBaseCont;
    private ContainerNode buildBaseContWithList;
    private MapEntryNode buildListEntry;
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
        this.refSchemaCtx = new SchemaContextRef(
                YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT)));
        this.schema = this.refSchemaCtx.get();

        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerQname = QName.create(baseQName, "player");
        final QName leafQname = QName.create(baseQName, "gap");
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");

        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQname, "name of band");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey2 =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQname, "name of band 2");

        this.iid = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerQname)
                .node(leafQname)
                .build();
        this.schemaNode = DataSchemaContextTree.from(this.schema).getChild(this.iid).getDataSchemaNode();

        this.iid2 = YangInstanceIdentifier.builder()
                .node(baseQName)
                .build();
        this.schemaNode2 = DataSchemaContextTree.from(this.schema).getChild(this.iid2).getDataSchemaNode();

        this.iid3 = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(listQname)
                .node(nodeWithKey)
                .build();
        this.schemaNode3 = DataSchemaContextTree.from(this.schema).getChild(this.iid3).getDataSchemaNode();

        this.buildLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(leafQname))
                .withValue(0.2)
                .build();
        final ContainerNode buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerQname))
                .withChild(this.buildLeaf)
                .build();
        this.buildBaseCont = Builders.containerBuilder()
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
        this.buildListEntry = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content)
                .withChild(content2)
                .build();
        final LeafNode<Object> content3 = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of band 2")
                .build();
        final LeafNode<Object> content4 = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "description")))
                .withValue("band description 2")
                .build();
        final MapEntryNode buildListEntry2 = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey2)
                .withChild(content3)
                .withChild(content4)
                .build();
        final MapNode buildList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(listQname))
                .withChild(this.buildListEntry)
                .withChild(buildListEntry2)
                .build();
        this.buildBaseContWithList = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(baseQName))
                .withChild(buildList)
                .build();

    }

    @Test
    public void testValidInputData() throws Exception {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);
        PutDataTransactionUtil.validInputData(iidContext.getSchemaNode(), payload);
    }

    @Test
    public void testValidTopLevelNodeName() throws Exception {
        InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(iidContext.getInstanceIdentifier(), payload);

        iidContext = new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);
        PutDataTransactionUtil.validTopLevelNodeName(iidContext.getInstanceIdentifier(), payload);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testValidTopLevelNodeNamePathEmpty() throws Exception {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(YangInstanceIdentifier.EMPTY, payload);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testValidTopLevelNodeNameWrongTopIdentifier() throws Exception {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);
        PutDataTransactionUtil.validTopLevelNodeName(this.iid.getAncestor(1), payload);
    }

    @Test
    public void testValidateListKeysEqualityInPayloadAndUri() throws Exception {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid3, this.schemaNode3, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildListEntry);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);
    }

    @Test
    public void testPutContainerData() throws Exception {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(Optional.absent()))
                .when(this.read).read(LogicalDatastoreType.CONFIGURATION, this.iid2);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();

        PutDataTransactionUtil.putData(payload, this.refSchemaCtx,
                new TransactionVarsWrapper(payload.getInstanceIdentifierContext(), null, this.transactionChain), null,
                null);
        verify(this.read).read(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
    }

    @Test
    public void testPutleafData() throws Exception {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid, this.schemaNode, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildLeaf);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(Optional.absent()))
                .when(this.read).read(LogicalDatastoreType.CONFIGURATION, this.iid);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();

        PutDataTransactionUtil.putData(payload, this.refSchemaCtx,
                new TransactionVarsWrapper(payload.getInstanceIdentifierContext(), null, this.transactionChain), null,
                null);
        verify(this.read).read(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier());
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
    }

    @Test
    public void testPutListData() throws Exception {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, this.schemaNode2, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseContWithList);

        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.write).when(this.transactionChain).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(Optional.absent()))
                .when(this.read).read(LogicalDatastoreType.CONFIGURATION, this.iid2);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        PutDataTransactionUtil.putData(payload, this.refSchemaCtx,
                new TransactionVarsWrapper(payload.getInstanceIdentifierContext(), null, this.transactionChain), null,
                null);
        verify(this.read).read(LogicalDatastoreType.CONFIGURATION, this.iid2);
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, this.iid2, payload.getData());
    }

}


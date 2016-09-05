/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
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
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.util.SingletonSet;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.w3c.dom.DOMException;

public class PostDataTransactionUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";

    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private DOMDataReadWriteTransaction readWrite;
    @Mock
    private DOMDataReadOnlyTransaction read;
    @Mock
    private DOMDataWriteTransaction write;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private UriBuilder uriBuilder;

    private SchemaContextRef refSchemaCtx;
    private ContainerNode buildBaseCont;
    private SchemaContext schema;
    private YangInstanceIdentifier iid2;
    private MapNode buildList;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        refSchemaCtx = new SchemaContextRef(TestRestconfUtils.loadSchemaContext(PATH_FOR_NEW_SCHEMA_CONTEXT));
        schema = refSchemaCtx.get();

        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerQname = QName.create(baseQName, "player");
        final QName leafQname = QName.create(baseQName, "gap");
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQname, "name of band");
        iid2 = YangInstanceIdentifier.builder()
                .node(baseQName)
                .build();

        final LeafNode buildLeaf = Builders.leafBuilder()
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
        final MapEntryNode mapEntryNode = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content)
                .withChild(content2)
                .build();
        buildList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(listQname))
                .withChild(mapEntryNode)
                .build();

        doReturn(UriBuilder.fromUri("http://localhost:8181/restconf/15/")).when(uriInfo).getBaseUriBuilder();
        doReturn(readWrite).when(transactionChain).newReadWriteTransaction();
        doReturn(read).when(transactionChain).newReadOnlyTransaction();
    }

    @Test
    public void testPostContainerData() {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iid2, null, null, schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, buildBaseCont);

        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION, iid2);
        final YangInstanceIdentifier.NodeIdentifier identifier = ((ContainerNode) ((SingletonSet) payload.getData().getValue()).iterator().next()).getIdentifier();
        final YangInstanceIdentifier node = payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(Futures.immediateCheckedFuture(false)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(readWrite).submit();
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(payload.getInstanceIdentifierContext(), null, transactionChain);
        final Response response = PostDataTransactionUtil.postData(uriInfo, payload, wrapper, refSchemaCtx);
        assertEquals(201, response.getStatus());
        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, (NormalizedNode<?, ?>) ((SingletonSet) payload.getData().getValue()).iterator().next());
    }

    @Test
    public void testPostListData() {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iid2, null, null, schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, buildList);

        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION, iid2);
        final MapNode data = (MapNode) payload.getData();
        final YangInstanceIdentifier.NodeIdentifierWithPredicates identifier = data.getValue().iterator().next().getIdentifier();
        final YangInstanceIdentifier node = payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(Futures.immediateCheckedFuture(false)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(readWrite).submit();
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(payload.getInstanceIdentifierContext(), null, transactionChain);
        final Response response = PostDataTransactionUtil.postData(uriInfo, payload, wrapper, refSchemaCtx);
        assertEquals(201, response.getStatus());
        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, data.getValue().iterator().next());
    }

    @Test
    public void testPostDataFail() {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iid2, null, null, schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, buildBaseCont);

        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION, iid2);
        final YangInstanceIdentifier.NodeIdentifier identifier = ((ContainerNode) ((SingletonSet) payload.getData().getValue()).iterator().next()).getIdentifier();
        final YangInstanceIdentifier node = payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(Futures.immediateCheckedFuture(false)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node,
                payload.getData());
        doReturn(Futures.immediateFailedCheckedFuture(new DOMException((short) 414, "Post request failed"))).when(readWrite).submit();
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(payload.getInstanceIdentifierContext(), null, transactionChain);
        final Response response = PostDataTransactionUtil.postData(uriInfo, payload, wrapper, refSchemaCtx);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, response.getStatusInfo());
        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, (NormalizedNode<?, ?>) ((SingletonSet) payload.getData().getValue()).iterator().next());
    }

}

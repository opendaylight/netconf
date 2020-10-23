/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.DOMException;

public class PostDataTransactionUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";

    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    private TransactionChainHandler transactionChainHandler;
    private ContainerNode buildBaseCont;
    private EffectiveModelContext schema;
    private YangInstanceIdentifier iid2;
    private YangInstanceIdentifier iidList;
    private MapNode buildList;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.schema =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));

        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerQname = QName.create(baseQName, "player");
        final QName leafQname = QName.create(baseQName, "gap");
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");
        final NodeIdentifierWithPredicates nodeWithKey = NodeIdentifierWithPredicates.of(listQname, listKeyQname,
            "name of band");
        this.iid2 = YangInstanceIdentifier.builder()
                .node(baseQName)
                .build();
        this.iidList = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(listQname)
                .build();

        final LeafNode<?> buildLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(leafQname))
                .withValue(0.2)
                .build();
        final ContainerNode buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(containerQname))
                .withChild(buildLeaf)
                .build();
        this.buildBaseCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(baseQName))
                .withChild(buildPlayerCont)
                .build();

        final LeafNode<Object> content = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of band")
                .build();
        final LeafNode<Object> content2 = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "description")))
                .withValue("band description")
                .build();
        final MapEntryNode mapEntryNode = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content)
                .withChild(content2)
                .build();
        this.buildList = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(listQname))
                .withChild(mapEntryNode)
                .build();

        doReturn(UriBuilder.fromUri("http://localhost:8181/restconf/16/")).when(this.uriInfo).getBaseUriBuilder();
        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();

        Mockito.doReturn(transactionChain).when(mockDataBroker).createTransactionChain(Mockito.any());
        transactionChainHandler = new TransactionChainHandler(mockDataBroker);
    }

    @Test
    public void testPostContainerData() {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, null, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(immediateFalseFluentFuture()).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION,
            this.iid2);
        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).getConfig(this.iid2);
        final NodeIdentifier identifier =
                ((ContainerNode) ((Collection<?>) payload.getData().getValue()).iterator().next()).getIdentifier();
        final YangInstanceIdentifier node =
                payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(immediateFalseFluentFuture()).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, node, payload.getData());
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        Response response = PostDataTransactionUtil.postData(this.uriInfo, payload,
                        new MdsalRestconfStrategy(transactionChainHandler), this.schema, null, null);
        assertEquals(201, response.getStatus());
        verify(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iid2);
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());

        response = PostDataTransactionUtil.postData(this.uriInfo, payload,
                new NetconfRestconfStrategy(netconfService), this.schema, null, null);
        assertEquals(201, response.getStatus());
        verify(this.netconfService).getConfig(this.iid2);
        verify(this.netconfService).create(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(), Optional.empty());
    }

    @Test
    public void testPostListData() throws UnsupportedEncodingException {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidList, null, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildList);

        final MapNode data = (MapNode) payload.getData();
        final NodeIdentifierWithPredicates identifier = data.getValue().iterator().next().getIdentifier();
        final YangInstanceIdentifier node =
                payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(immediateFalseFluentFuture()).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).getConfig(node);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, node, payload.getData());
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();
        doReturn(CommitInfo.emptyFluentFuture()).when(this.netconfService).commit(Mockito.any());

        Response response = PostDataTransactionUtil.postData(this.uriInfo, payload,
                        new MdsalRestconfStrategy(transactionChainHandler), this.schema, null, null);
        assertEquals(201, response.getStatus());
        assertThat(URLDecoder.decode(response.getLocation().toString(), StandardCharsets.UTF_8),
            containsString(identifier.getValue(identifier.keySet().iterator().next()).toString()));
        verify(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, node, data.getValue().iterator().next());

        response = PostDataTransactionUtil.postData(this.uriInfo, payload,
                new NetconfRestconfStrategy(netconfService), this.schema, null, null);
        assertEquals(201, response.getStatus());
        assertThat(URLDecoder.decode(response.getLocation().toString(), StandardCharsets.UTF_8),
                containsString(identifier.getValue(identifier.keySet().iterator().next()).toString()));
        verify(this.netconfService).getConfig(node);
        verify(this.netconfService).create(LogicalDatastoreType.CONFIGURATION, node, data.getValue().iterator().next(),
                Optional.empty());
    }

    @Test
    public void testPostDataFail() {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iid2, null, null, this.schema);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(immediateFalseFluentFuture()).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION,
            this.iid2);
        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).getConfig(this.iid2);
        final NodeIdentifier identifier =
                ((ContainerNode) ((Collection<?>) payload.getData().getValue()).iterator().next()).getIdentifier();
        final YangInstanceIdentifier node =
                payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(immediateFalseFluentFuture()).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).getConfig(node);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, node, payload.getData());
        final DOMException domException = new DOMException((short) 414, "Post request failed");
        doReturn(immediateFailedFluentFuture(domException)).when(this.readWrite).commit();
        doReturn(immediateFailedFluentFuture(domException)).when(this.netconfService).commit(Mockito.any());

        try {
            PostDataTransactionUtil.postData(this.uriInfo, payload,
                    new MdsalRestconfStrategy(transactionChainHandler), this.schema, null, null);
            fail("Expected RestconfDocumentedException");
        } catch (final RestconfDocumentedException e) {
            assertEquals(1, e.getErrors().size());
            assertTrue(e.getErrors().get(0).getErrorInfo().contains(domException.getMessage()));
        }

        verify(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iid2);
        verify(this.readWrite).put(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData());

        try {
            PostDataTransactionUtil.postData(this.uriInfo, payload,
                    new NetconfRestconfStrategy(netconfService), this.schema, null, null);
            fail("Expected RestconfDocumentedException");
        } catch (final RestconfDocumentedException e) {
            assertEquals(1, e.getErrors().size());
            assertTrue(e.getErrors().get(0).getErrorInfo().contains(domException.getMessage()));
        }

        verify(this.netconfService).getConfig(this.iid2);
        verify(this.netconfService).create(LogicalDatastoreType.CONFIGURATION,
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(), Optional.empty());
    }
}

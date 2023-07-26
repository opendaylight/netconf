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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;

import com.google.common.util.concurrent.Futures;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.DOMException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PostDataTransactionUtilTest {
    private static EffectiveModelContext SCHEMA;

    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    private ContainerNode buildBaseCont;
    private YangInstanceIdentifier iid2;
    private YangInstanceIdentifier iidList;
    private MapNode buildList;

    @BeforeClass
    public static void beforeClass() throws Exception {
        SCHEMA = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/jukebox"));
    }

    @Before
    public void setUp() {
        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerQname = QName.create(baseQName, "player");
        final QName leafQname = QName.create(baseQName, "gap");
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");
        final NodeIdentifierWithPredicates nodeWithKey = NodeIdentifierWithPredicates.of(listQname, listKeyQname,
            "name of band");
        iid2 = YangInstanceIdentifier.builder().node(baseQName).build();
        iidList = YangInstanceIdentifier.builder().node(baseQName).node(listQname).build();

        buildBaseCont = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(baseQName))
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(containerQname))
                .withChild(ImmutableNodes.leafNode(leafQname, 0.2))
                .build())
            .build();

        buildList = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(listQname))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(ImmutableNodes.leafNode(QName.create(baseQName, "name"), "name of band"))
                .withChild(ImmutableNodes.leafNode(QName.create(baseQName, "description"), "band description"))
                .build())
            .build();

        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();

        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
    }

    @Test
    public void testPostContainerData() {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        final NodeIdentifier identifier = buildBaseCont.body().iterator().next().name();
        final YangInstanceIdentifier node = iid2.node(identifier);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node.getParent(), buildBaseCont);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont, Optional.empty());

        Response response = PostDataTransactionUtil.postData(uriInfo, iid2, buildBaseCont,
            new MdsalRestconfStrategy(mockDataBroker), SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont);

        response = PostDataTransactionUtil.postData(uriInfo, iid2, buildBaseCont,
                new NetconfRestconfStrategy(netconfService), SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        verify(netconfService).create(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont, Optional.empty());
    }

    @Test
    public void testPostListData() {
        final MapEntryNode entryNode = buildList.body().iterator().next();
        final NodeIdentifierWithPredicates identifier = entryNode.name();
        final YangInstanceIdentifier node = iidList.node(identifier);
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .merge(any(), any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).create(
            LogicalDatastoreType.CONFIGURATION, node, entryNode, Optional.empty());

        Response response = PostDataTransactionUtil.postData(uriInfo, iidList, buildList,
                        new MdsalRestconfStrategy(mockDataBroker), SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        assertThat(URLDecoder.decode(response.getLocation().toString(), StandardCharsets.UTF_8),
            containsString(identifier.getValue(identifier.keySet().iterator().next()).toString()));
        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);

        response = PostDataTransactionUtil.postData(uriInfo, iidList, buildList,
                new NetconfRestconfStrategy(netconfService), SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        assertThat(URLDecoder.decode(response.getLocation().toString(), StandardCharsets.UTF_8),
                containsString(identifier.getValue(identifier.keySet().iterator().next()).toString()));
        verify(netconfService).create(LogicalDatastoreType.CONFIGURATION, node, entryNode,
                Optional.empty());
    }

    @Test
    public void testPostDataFail() {
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        final NodeIdentifier identifier = buildBaseCont.body().iterator().next().name();
        final YangInstanceIdentifier node = iid2.node(identifier);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node.getParent(), buildBaseCont);
        final DOMException domException = new DOMException((short) 414, "Post request failed");
        doReturn(immediateFailedFluentFuture(domException)).when(readWrite).commit();
        doReturn(immediateFailedFluentFuture(domException)).when(netconfService)
            .create(any(), any(), any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();

        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> PostDataTransactionUtil.postData(uriInfo, iid2, buildBaseCont,
                new MdsalRestconfStrategy(mockDataBroker), SCHEMA, WriteDataParams.empty()));
        assertEquals(1, ex.getErrors().size());
        assertThat(ex.getErrors().get(0).getErrorInfo(), containsString(domException.getMessage()));

        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont);

        ex = assertThrows(RestconfDocumentedException.class, () -> PostDataTransactionUtil.postData(uriInfo, iid2,
            buildBaseCont, new NetconfRestconfStrategy(netconfService), SCHEMA, WriteDataParams.empty()));
        assertEquals(1, ex.getErrors().size());
        assertThat(ex.getErrors().get(0).getErrorInfo(), containsString(domException.getMessage()));

        verify(netconfService).create(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont, Optional.empty());
    }
}

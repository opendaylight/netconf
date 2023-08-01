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
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.w3c.dom.DOMException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PostDataTransactionUtilTest extends AbstractJukeboxTest {
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

    @Before
    public void setUp() {
        iid2 = YangInstanceIdentifier.of(JUKEBOX_QNAME);
        iidList = YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYLIST_QNAME);

        buildBaseCont = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(PLAYER_QNAME))
                .withChild(ImmutableNodes.leafNode(GAP_QNAME, 0.2))
                .build())
            .build();

        buildList = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description"))
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
            new MdsalRestconfStrategy(mockDataBroker), JUKEBOX_SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont);

        response = PostDataTransactionUtil.postData(uriInfo, iid2, buildBaseCont,
                new NetconfRestconfStrategy(netconfService), JUKEBOX_SCHEMA, WriteDataParams.empty());
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
                        new MdsalRestconfStrategy(mockDataBroker), JUKEBOX_SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        assertThat(URLDecoder.decode(response.getLocation().toString(), StandardCharsets.UTF_8),
            containsString(identifier.getValue(identifier.keySet().iterator().next()).toString()));
        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);

        response = PostDataTransactionUtil.postData(uriInfo, iidList, buildList,
                new NetconfRestconfStrategy(netconfService), JUKEBOX_SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        assertThat(URLDecoder.decode(response.getLocation().toString(), StandardCharsets.UTF_8),
                containsString(identifier.getValue(identifier.keySet().iterator().next()).toString()));
        verify(netconfService).create(LogicalDatastoreType.CONFIGURATION, node, entryNode, Optional.empty());
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
                new MdsalRestconfStrategy(mockDataBroker), JUKEBOX_SCHEMA, WriteDataParams.empty()));
        assertEquals(1, ex.getErrors().size());
        assertThat(ex.getErrors().get(0).getErrorInfo(), containsString(domException.getMessage()));

        verify(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iid2);
        verify(readWrite).put(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont);

        ex = assertThrows(RestconfDocumentedException.class, () -> PostDataTransactionUtil.postData(uriInfo, iid2,
            buildBaseCont, new NetconfRestconfStrategy(netconfService), JUKEBOX_SCHEMA, WriteDataParams.empty()));
        assertEquals(1, ex.getErrors().size());
        assertThat(ex.getErrors().get(0).getErrorInfo(), containsString(domException.getMessage()));

        verify(netconfService).create(LogicalDatastoreType.CONFIGURATION, iid2, buildBaseCont, Optional.empty());
    }
}

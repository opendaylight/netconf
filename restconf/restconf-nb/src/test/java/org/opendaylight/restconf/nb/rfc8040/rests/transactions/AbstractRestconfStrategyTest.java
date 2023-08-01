/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.DeleteDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PlainPatchDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.w3c.dom.DOMException;

abstract class AbstractRestconfStrategyTest extends AbstractJukeboxTest {
    static final ContainerNode JUKEBOX_WITH_BANDS = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(BAND_ENTRY)
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band 2"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band 2"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description 2"))
                .build())
            .build())
        .build();
    static final ContainerNode JUKEBOX_WITH_PLAYLIST = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "MyFavoriteBand-A"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "MyFavoriteBand-A"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description A"))
                .build())
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "MyFavoriteBand-B"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "MyFavoriteBand-B"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description B"))
                .build())
            .build())
        .build();
    static final MapNode PLAYLIST = Builders.mapBuilder()
        .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
        .withChild(Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band"))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band"))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description"))
            .build())
        .build();
    static final YangInstanceIdentifier PLAYLIST_IID = YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYLIST_QNAME);

    @Mock
    private UriInfo uriInfo;

    /**
     * Test of successful DELETE operation.
     */
    @Test
    public final void testDeleteData() {
        final var response = DeleteDataTransactionUtil.deleteData(testDeleteDataStrategy(),
            YangInstanceIdentifier.of());
        // assert success
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    abstract @NonNull RestconfStrategy testDeleteDataStrategy();

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error DATA_MISSING is expected.
     */
    @Test
    public final void testNegativeDeleteData() {
        final var strategy = testNegativeDeleteDataStrategy();
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> DeleteDataTransactionUtil.deleteData(strategy, YangInstanceIdentifier.of()));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    abstract @NonNull RestconfStrategy testNegativeDeleteDataStrategy();

    @Test
    public final void testPostContainerData() {
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        final var response = PostDataTransactionUtil.postData(uriInfo, JUKEBOX_IID, EMPTY_JUKEBOX,
            testPostContainerDataStrategy(), JUKEBOX_SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
    }

    abstract @NonNull RestconfStrategy testPostContainerDataStrategy();

    @Test
    public final void testPostListData() {
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        final var entryNode = PLAYLIST.body().iterator().next();
        final var identifier = entryNode.name();
        final var response = PostDataTransactionUtil.postData(uriInfo, PLAYLIST_IID, PLAYLIST,
            testPostListDataStrategy(entryNode, PLAYLIST_IID.node(identifier)), JUKEBOX_SCHEMA,
            WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        assertThat(URLDecoder.decode(response.getLocation().toString(), StandardCharsets.UTF_8),
            containsString(identifier.getValue(identifier.keySet().iterator().next()).toString()));
    }

    abstract @NonNull RestconfStrategy testPostListDataStrategy(MapEntryNode entryNode, YangInstanceIdentifier node);

    @Test
    public final void testPostDataFail() {
        final var domException = new DOMException((short) 414, "Post request failed");

        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> PostDataTransactionUtil.postData(uriInfo, JUKEBOX_IID, EMPTY_JUKEBOX,
                testPostDataFailStrategy(domException), JUKEBOX_SCHEMA, WriteDataParams.empty()));
        assertEquals(1, ex.getErrors().size());
        assertThat(ex.getErrors().get(0).getErrorInfo(), containsString(domException.getMessage()));
    }

    abstract @NonNull RestconfStrategy testPostDataFailStrategy(DOMException domException);

    @Test
    public final void testPatchContainerData() {
        final var response = PlainPatchDataTransactionUtil.patchData(JUKEBOX_IID, EMPTY_JUKEBOX,
            testPatchContainerDataStrategy(), JUKEBOX_SCHEMA);
        assertEquals(200, response.getStatus());
    }

    abstract @NonNull RestconfStrategy testPatchContainerDataStrategy();

    @Test
    public final void testPatchLeafData() {
        final var response = PlainPatchDataTransactionUtil.patchData(GAP_IID, GAP_LEAF,
            testPatchLeafDataStrategy(), JUKEBOX_SCHEMA);
        assertEquals(200, response.getStatus());
    }

    abstract @NonNull RestconfStrategy testPatchLeafDataStrategy();

    @Test
    public final void testPatchListData() {
        final var response = PlainPatchDataTransactionUtil.patchData(JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST,
            testPatchListDataStrategy(), JUKEBOX_SCHEMA);
        assertEquals(200, response.getStatus());
    }

    abstract @NonNull RestconfStrategy testPatchListDataStrategy();
}

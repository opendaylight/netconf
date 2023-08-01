/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;

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
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

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
}

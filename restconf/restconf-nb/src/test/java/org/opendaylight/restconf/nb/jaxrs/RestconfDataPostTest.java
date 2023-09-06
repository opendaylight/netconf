/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.net.URI;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

@ExtendWith(MockitoExtension.class)
class RestconfDataPostTest extends AbstractRestconfTest {
    @Mock
    private DOMDataTreeReadWriteTransaction tx;

    @BeforeEach
    void beforeEach() {
        lenient().doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();
        doReturn(tx).when(dataBroker).newReadWriteTransaction();
    }

    @Test
    void testPostData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(tx).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID,
            Builders.containerBuilder().withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME)).build());
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        assertEquals(URI.create("http://localhost:8181/rests/data/example-jukebox:jukebox"),
            assertResponse(201, ar -> restconf.postDataJSON(stringInputStream("""
                {
                  "example-jukebox:jukebox" : {
                  }
                }"""), uriInfo, ar)).getLocation());
    }

    @Test
    public void testPostMapEntryData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        final var node = PLAYLIST_IID.node(BAND_ENTRY.name());
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(tx).put(LogicalDatastoreType.CONFIGURATION, node, BAND_ENTRY);
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        assertEquals(URI.create("http://localhost:8181/rests/data/example-jukebox:jukebox/playlist=name%20of%20band"),
            assertResponse(201, ar -> restconf.postDataJSON(JUKEBOX_API_PATH, stringInputStream("""
                {
                  "example-jukebox:playlist" : {
                    "name" : "name of band",
                    "description" : "band description"
                  }
                }"""), uriInfo, ar)).getLocation());
    }

    @Test
    public void testPostExistingData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateTrueFluentFuture())
            .when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        final var mockAsyncResponse = mock(AsyncResponse.class);

        final var ex = assertThrows(RestconfDocumentedException.class, () -> {
            restconf.postDataJSON(stringInputStream("""
                    {
                      "example-jukebox:jukebox" : {
                      }
                    }"""),
                uriInfo, mockAsyncResponse);
        });

        final var error = ex.getErrors().get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_EXISTS, error.getErrorTag());
    }
}

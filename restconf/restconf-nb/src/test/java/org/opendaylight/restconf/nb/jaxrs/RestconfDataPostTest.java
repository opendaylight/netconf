/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

@ExtendWith(MockitoExtension.class)
class RestconfDataPostTest extends AbstractRestconfTest {
    private static final String BASE_URI = "http://localhost:8181/rests/";
    private static final String INSERT = "insert";
    private static final String POINT = "point";
    @Mock
    private DOMDataTreeReadWriteTransaction tx;
    @Mock
    private DOMDataTreeReadTransaction readTx;

    @BeforeEach
    void beforeEach() {
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();
        doReturn(tx).when(dataBroker).newReadWriteTransaction();
    }

    @Test
    void testPostData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(tx).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID,
            Builders.containerBuilder().withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME)).build());
        doReturn(UriBuilder.fromUri(BASE_URI)).when(uriInfo).getBaseUriBuilder();

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
        doReturn(UriBuilder.fromUri(BASE_URI)).when(uriInfo).getBaseUriBuilder();

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
    public void testPostDataWithInsertLast() {
        // Mocking the query parameters to include 'insert=last'
        final var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.put(INSERT, List.of("last"));
        doReturn(queryParams).when(uriInfo).getQueryParameters();

        doReturn(immediateFalseFluentFuture()).when(tx)
                .exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        doNothing().when(tx).put(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class),
                any(NormalizedNode.class));
        doReturn(UriBuilder.fromUri(BASE_URI)).when(uriInfo).getBaseUriBuilder();

        assertEquals(URI.create("http://localhost:8181/rests/data/example-jukebox:jukebox/playlist=0/song=3"),
            assertResponse(201, ar -> restconf.postDataJSON(
                apiPath("example-jukebox:jukebox/playlist=0"), stringInputStream("""
                    {
                      "example-jukebox:song" : [
                        {
                           "index": "3"
                        }
                      ]
                    }"""), uriInfo, ar)).getLocation());
    }

    @Test
    public void testPostDataWithInsertFirst() {
        // Mocking the query parameters to include 'insert=first'
        final var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.put(INSERT, List.of("first"));
        doReturn(queryParams).when(uriInfo).getQueryParameters();
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();

        doReturn(immediateFalseFluentFuture()).when(readTx)
                .exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        // Mocking existed playlist with two songs in DS
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(tx)
                .read(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        doNothing().when(tx).put(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class),
                any(NormalizedNode.class));
        doReturn(UriBuilder.fromUri(BASE_URI)).when(uriInfo).getBaseUriBuilder();

        assertEquals(URI.create("http://localhost:8181/rests/data/example-jukebox:jukebox/playlist=0/song=3"),
            assertResponse(201, ar -> restconf.postDataJSON(
                apiPath("example-jukebox:jukebox/playlist=0"), stringInputStream("""
                    {
                      "example-jukebox:song" : [
                        {
                           "index": "3"
                        }
                      ]
                    }"""), uriInfo, ar)).getLocation());
    }

    @Test
    public void testPostDataWithInsertBefore() {
        // Mocking the query parameters to include 'insert=before' and 'point=example-jukebox:jukebox/playlist=0/song=2'
        final var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.put(INSERT, List.of("before"));
        queryParams.put(POINT, List.of("example-jukebox:jukebox/playlist=0/song=2"));
        doReturn(queryParams).when(uriInfo).getQueryParameters();
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();

        doReturn(immediateFalseFluentFuture()).when(readTx)
                .exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        // Mocking existed playlist with two songs in DS
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(tx)
                .read(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        doNothing().when(tx).put(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class),
                any(NormalizedNode.class));
        doReturn(UriBuilder.fromUri(BASE_URI)).when(uriInfo).getBaseUriBuilder();

        assertEquals(URI.create("http://localhost:8181/rests/data/example-jukebox:jukebox/playlist=0/song=3"),
            assertResponse(201, ar -> restconf.postDataJSON(
                apiPath("example-jukebox:jukebox/playlist=0"), stringInputStream("""
                    {
                      "example-jukebox:song" : [
                        {
                           "index": "3",
                           "id" = "C"
                        }
                      ]
                    }"""), uriInfo, ar)).getLocation());
    }

    @Test
    public void testPostDataWithInsertAfter() {
        // Mocking the query parameters to include 'insert=after' and 'point=example-jukebox:jukebox/playlist=0/song=1'
        final var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.put(INSERT, List.of("after"));
        queryParams.put(POINT, List.of("example-jukebox:jukebox/playlist=0/song=1"));
        doReturn(queryParams).when(uriInfo).getQueryParameters();
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();

        doReturn(immediateFalseFluentFuture()).when(readTx)
                .exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(tx)
                .read(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        doNothing().when(tx).put(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class),
                any(NormalizedNode.class));
        doReturn(UriBuilder.fromUri(BASE_URI)).when(uriInfo).getBaseUriBuilder();

        assertEquals(URI.create("http://localhost:8181/rests/data/example-jukebox:jukebox/playlist=0/song=3"),
            assertResponse(201, ar -> restconf.postDataJSON(
                apiPath("example-jukebox:jukebox/playlist=0"), stringInputStream("""
                    {
                      "example-jukebox:song" : [
                        {
                           "index": "3",
                           "id" = "C"
                        }
                      ]
                    }"""), uriInfo, ar)).getLocation());
    }
}

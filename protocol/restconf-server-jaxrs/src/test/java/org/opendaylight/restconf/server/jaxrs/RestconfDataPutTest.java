/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@ExtendWith(MockitoExtension.class)
class RestconfDataPutTest extends AbstractRestconfTest {
    private final MultivaluedMap<String, String> queryParamenters = new MultivaluedHashMap<>();

    @Mock
    private DOMDataTreeReadTransaction readTx;
    @Mock
    private DOMDataTreeReadWriteTransaction rwTx;

    @BeforeEach
    void beforeEach() {
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(rwTx).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(rwTx).commit();
    }

    @Test
    void testPutData() {
        doReturn(queryParamenters).when(uriInfo).getQueryParameters();
        doReturn(immediateTrueFluentFuture()).when(readTx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(rwTx).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);

        assertNull(assertEntity(204, ar -> restconf.dataJsonPUT(JUKEBOX_API_PATH, uriInfo, sc, stringInputStream("""
            {
              "example-jukebox:jukebox" : {
                "player": {
                  "gap": "0.2"
                }
              }
            }"""), ar)));
    }

    @Test
    void testPutDataWithMountPoint() {
        doReturn(queryParamenters).when(uriInfo).getQueryParameters();
        doReturn(immediateTrueFluentFuture()).when(readTx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(rwTx).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any());
        doReturn(Optional.empty()).when(mountPoint).getService(DOMServerStrategy.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);

        assertNull(assertEntity(204, ar -> restconf.dataXmlPUT(
            apiPath("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox"), uriInfo, sc,
            stringInputStream("""
                <jukebox xmlns="http://example.com/ns/example-jukebox">
                  <player>
                    <gap>0.2</gap>
                  </player>
                </jukebox>"""), ar)));
    }

    @Test
    public void testPutDataWithInsertLast() {
        // Mocking the query parameters to include 'insert=last'
        final var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.put("insert", List.of("last"));
        doReturn(queryParams).when(uriInfo).getQueryParameters();

        doReturn(immediateFalseFluentFuture()).when(readTx)
            .exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        assertNull(assertEntity(201, ar -> restconf.dataJsonPUT(
            apiPath("example-jukebox:jukebox/playlist=0/song=3"), uriInfo, sc, stringInputStream("""
            {
              "example-jukebox:song" : [
                {
                   "index": "3"
                }
              ]
            }"""), ar)));
    }

    @Test
    public void testPutDataWithInsertFirst() {
        // Mocking the query parameters to include 'insert=first'
        final var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.put("insert", List.of("first"));
        doReturn(queryParams).when(uriInfo).getQueryParameters();

        doReturn(immediateFalseFluentFuture()).when(readTx)
            .exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        // Mocking existed playlist with two songs in DS
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(rwTx)
            .read(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        assertNull(assertEntity(201, ar -> restconf.dataJsonPUT(
            apiPath("example-jukebox:jukebox/playlist=0/song=3"), uriInfo, sc, stringInputStream("""
            {
              "example-jukebox:song" : [
                {
                   "index": "3"
                }
              ]
            }"""), ar)));
    }

    @Test
    public void testPutDataWithInsertBefore() {
        // Mocking the query parameters to include 'insert=before' and 'point=example-jukebox:jukebox/playlist=0/song=2'
        final var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.put("insert", List.of("before"));
        queryParams.put("point", List.of("example-jukebox:jukebox/playlist=0/song=2"));
        doReturn(queryParams).when(uriInfo).getQueryParameters();

        doReturn(immediateFalseFluentFuture()).when(readTx)
            .exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        // Mocking existed playlist with two songs in DS
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(rwTx)
            .read(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        assertNull(assertEntity(201, ar -> restconf.dataJsonPUT(
            apiPath("example-jukebox:jukebox/playlist=0/song=3"), uriInfo, sc, stringInputStream("""
            {
              "example-jukebox:song" : [
                {
                   "index": "3",
                   "id" = "C"
                }
              ]
            }"""), ar)));
    }

    @Test
    public void testPutDataWithInsertAfter() {
        // Mocking the query parameters to include 'insert=after' and 'point=example-jukebox:jukebox/playlist=0/song=1'
        final var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.put("insert", List.of("after"));
        queryParams.put("point", List.of("example-jukebox:jukebox/playlist=0/song=1"));
        doReturn(queryParams).when(uriInfo).getQueryParameters();

        doReturn(immediateFalseFluentFuture()).when(readTx)
            .exists(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));
        doReturn(immediateFluentFuture(Optional.of(PLAYLIST_WITH_SONGS))).when(rwTx)
            .read(eq(LogicalDatastoreType.CONFIGURATION), any(YangInstanceIdentifier.class));

        assertNull(assertEntity(201, ar -> restconf.dataJsonPUT(
            apiPath("example-jukebox:jukebox/playlist=0/song=3"), uriInfo, sc, stringInputStream("""
            {
              "example-jukebox:song" : [
                {
                   "index": "3",
                   "id" = "C"
                }
              ]
            }"""), ar)));
    }
}

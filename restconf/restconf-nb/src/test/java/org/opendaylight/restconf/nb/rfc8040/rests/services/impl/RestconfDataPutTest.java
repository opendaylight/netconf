/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

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
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;

@ExtendWith(MockitoExtension.class)
class RestconfDataPutTest extends AbstractRestconfTest {
    private final MultivaluedMap<String, String> queryParamenters = new MultivaluedHashMap<>();

    @Mock
    private DOMDataTreeReadTransaction readTx;
    @Mock
    private DOMDataTreeReadWriteTransaction rwTx;

    @BeforeEach
    void beforeEach() {
        doReturn(queryParamenters).when(uriInfo).getQueryParameters();
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(rwTx).when(dataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(rwTx).commit();
    }

    @Test
    void testPutData() {
        doReturn(immediateTrueFluentFuture()).when(readTx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(rwTx).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);

        assertNull(assertResponse(204, ar -> restconf.dataJsonPUT("example-jukebox:jukebox", uriInfo,
            stringInputStream("""
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
        doReturn(immediateTrueFluentFuture()).when(readTx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(rwTx).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any());
        doReturn(Optional.of(FixedDOMSchemaService.of(JUKEBOX_SCHEMA))).when(mountPoint)
        .getService(DOMSchemaService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);

        assertNull(assertResponse(204, ar -> restconf.dataXmlPUT(
            "example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox", uriInfo, stringInputStream("""
            <jukebox xmlns="http://example.com/ns/example-jukebox">
              <player>
                <gap>0.2</gap>
              </player>
            </jukebox>"""), ar)));
    }
}

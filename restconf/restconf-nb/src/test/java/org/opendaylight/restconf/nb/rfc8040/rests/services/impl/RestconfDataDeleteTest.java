/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@ExtendWith(MockitoExtension.class)
class RestconfDataDeleteTest extends AbstractRestconfTest {
    @Mock
    private DOMDataTreeReadWriteTransaction tx;

    @BeforeEach
    void beforeEach() {
        doReturn(tx).when(dataBroker).newReadWriteTransaction();
    }

    @Test
    public void testDeleteData() {
        doNothing().when(tx).delete(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture())
                .when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();
        doReturn(true).when(asyncResponse).resume(responseCaptor.capture());
        restconf.dataDELETE("example-jukebox:jukebox", asyncResponse);

        assertEquals(204, responseCaptor.getValue().getStatus());
    }

    @Test
    public void testDeleteDataNotExisting() {
        doReturn(immediateFalseFluentFuture())
                .when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(true).when(tx).cancel();

        doReturn(true).when(asyncResponse).resume(exceptionCaptor.capture());
        restconf.dataDELETE("example-jukebox:jukebox", asyncResponse);

        final var errors = exceptionCaptor.getValue().getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Test of deleting data on mount point.
     */
    @Test
    public void testDeleteDataMountPoint() {
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(FixedDOMSchemaService.of(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);

        doNothing().when(tx).delete(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture())
                .when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();
        doReturn(true).when(asyncResponse).resume(responseCaptor.capture());
        restconf.dataDELETE("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox", asyncResponse);

        assertEquals(204, responseCaptor.getValue().getStatus());
    }
}

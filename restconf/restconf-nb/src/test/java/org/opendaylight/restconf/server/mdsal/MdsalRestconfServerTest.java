/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@ExtendWith(MockitoExtension.class)
class MdsalRestconfServerTest extends AbstractJukeboxTest {
    private static final DatabindProvider DATABIND_PROVIDER = () -> JUKEBOX_DATABIND;

    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private NetconfDataTreeService netconfService;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMActionService actionService;

    private MdsalRestconfServer server;

    @BeforeEach
    void before() {
        server = new MdsalRestconfServer(DATABIND_PROVIDER, dataBroker, rpcService, actionService, mountPointService);
    }

    @Test
    void testGetRestconfStrategyLocal() {
        assertInstanceOf(MdsalRestconfStrategy.class, server.getRestconfStrategy(JUKEBOX_SCHEMA, null));
    }

    @Test
    void testGetRestconfStrategyMountDataBroker() {
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        assertInstanceOf(MdsalRestconfStrategy.class, server.getRestconfStrategy(JUKEBOX_SCHEMA, mountPoint));
    }

    @Test
    void testGetRestconfStrategyMountNetconfService() {
        doReturn(Optional.of(netconfService)).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        assertInstanceOf(NetconfRestconfStrategy.class, server.getRestconfStrategy(JUKEBOX_SCHEMA, mountPoint));
    }

    @Test
    void testGetRestconfStrategyMountNone() {
        doReturn(JUKEBOX_IID).when(mountPoint).getIdentifier();
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> server.getRestconfStrategy(JUKEBOX_SCHEMA, mountPoint));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
        assertEquals("Could not find a supported access interface in mount point", error.getErrorMessage());
        assertEquals(JUKEBOX_IID, error.getErrorPath());
    }
}

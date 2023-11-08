/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import java.util.Optional;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class MdsalRestconfServerTest extends AbstractJukeboxTest {
    private static DatabindProvider DATABIND_PROVIDER;

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

    private MdsalRestconfServer server;

    @BeforeClass
    public static void setupDatabind() {
        DATABIND_PROVIDER = () -> DatabindContext.ofModel(JUKEBOX_SCHEMA);
    }

    @Before
    public void before() {
        server = new MdsalRestconfServer(DATABIND_PROVIDER, dataBroker, rpcService, mountPointService);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
    }

    @Test
    public void testGetRestconfStrategyLocal() {
        assertInstanceOf(MdsalRestconfStrategy.class, server.getRestconfStrategy(JUKEBOX_SCHEMA, null));
    }

    @Test
    public void testGetRestconfStrategyMountDataBroker() {
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        assertInstanceOf(MdsalRestconfStrategy.class, server.getRestconfStrategy(JUKEBOX_SCHEMA, mountPoint));
    }

    @Test
    public void testGetRestconfStrategyMountNetconfService() {
        doReturn(Optional.of(netconfService)).when(mountPoint).getService(NetconfDataTreeService.class);
        assertInstanceOf(NetconfRestconfStrategy.class, server.getRestconfStrategy(JUKEBOX_SCHEMA, mountPoint));
    }

    @Test
    public void testGetRestconfStrategyMountNone() {
        doReturn(JUKEBOX_IID).when(mountPoint).getIdentifier();
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMDataBroker.class);
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> server.getRestconfStrategy(JUKEBOX_SCHEMA, mountPoint));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
        assertEquals("Could not find a supported access interface in mount point "
            + "/(http://example.com/ns/example-jukebox?revision=2015-04-04)jukebox", error.getErrorMessage());
        // FIXME: should be JUKEBOX_IID
        assertNull(error.getErrorPath());
    }
}

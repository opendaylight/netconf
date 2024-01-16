/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.util.Optional;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@ExtendWith(MockitoExtension.class)
class RestconfDataGetTest extends AbstractRestconfTest {
    private static final NodeIdentifier PLAYLIST_NID = new NodeIdentifier(PLAYLIST_QNAME);
    private static final NodeIdentifier LIBRARY_NID = new NodeIdentifier(LIBRARY_QNAME);

    // config contains one child the same as in operational and one additional
    private static final ContainerNode CONFIG_JUKEBOX = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
            .withChild(CONT_PLAYER)
            .withChild(Builders.containerBuilder().withNodeIdentifier(LIBRARY_NID).build())
            .build();
    // operational contains one child the same as in config and one additional
    private static final ContainerNode OPER_JUKEBOX = Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
            .withChild(CONT_PLAYER)
            .withChild(Builders.mapBuilder().withNodeIdentifier(PLAYLIST_NID).build())
            .build();

    @Mock
    private DOMDataTreeReadTransaction tx;

    @BeforeEach
    void beforeEach() {
        doReturn(tx).when(dataBroker).newReadOnlyTransaction();
    }

    @Test
    void testReadData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(EMPTY_JUKEBOX))).when(tx)
                .read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(tx).read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);

        assertEquals(EMPTY_JUKEBOX, assertNormalizedNode(200, ar -> restconf.dataGET(JUKEBOX_API_PATH, uriInfo, ar)));
    }

    @Test
    void testReadRootData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(wrapNodeByDataRootContainer(CONFIG_JUKEBOX))))
                .when(tx)
                .read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        doReturn(immediateFluentFuture(Optional.of(wrapNodeByDataRootContainer(OPER_JUKEBOX))))
                .when(tx)
                .read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of());

        final var data = assertInstanceOf(ContainerNode.class,
            assertNormalizedNode(200, ar -> restconf.dataGET(uriInfo, ar)));
        final var rootNodes = data.body();
        assertEquals(1, rootNodes.size());
        final var allDataChildren = assertInstanceOf(ContainerNode.class, rootNodes.iterator().next()).body();
        assertEquals(3, allDataChildren.size());
    }

    private static ContainerNode wrapNodeByDataRootContainer(final DataContainerChild data) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(SchemaContext.NAME))
            .withChild(data)
            .build();
    }

    /**
     * Test read data from mount point when both {@link LogicalDatastoreType#CONFIGURATION} and
     * {@link LogicalDatastoreType#OPERATIONAL} contains the same data and some additional data to be merged.
     */
    @Test
    void testReadDataMountPoint() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(CONFIG_JUKEBOX))).when(tx)
                .read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFluentFuture(Optional.of(OPER_JUKEBOX))).when(tx)
                .read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);

        doReturn(Optional.of(mountPoint)).when(mountPointService)
            .getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);

        // response must contain all child nodes from config and operational containers merged in one container
        final var data = assertInstanceOf(ContainerNode.class,
            assertNormalizedNode(200, ar -> restconf.dataGET(
               apiPath("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox"), uriInfo, ar)));
        assertEquals(3, data.size());
        assertNotNull(data.childByArg(CONT_PLAYER.name()));
        assertNotNull(data.childByArg(LIBRARY_NID));
        assertNotNull(data.childByArg(PLAYLIST_NID));
    }

    @Test
    void testReadDataNoData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(tx).read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(tx).read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);

        final var error = assertError(ar -> restconf.dataGET(JUKEBOX_API_PATH, uriInfo, ar));
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals("Request could not be completed because the relevant data model content does not exist",
            error.getErrorMessage());
    }

    /**
     * Read data from config datastore according to content parameter.
     */
    @Test
    void testReadDataConfigTest() {
        final var parameters = new MultivaluedHashMap<String, String>();
        parameters.putSingle("content", "config");

        doReturn(parameters).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(CONFIG_JUKEBOX))).when(tx)
                .read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);

        // response must contain only config data
        final var data = assertInstanceOf(ContainerNode.class,
            assertNormalizedNode(200, ar -> restconf.dataGET(JUKEBOX_API_PATH, uriInfo, ar)));
        // config data present
        assertNotNull(data.childByArg(CONT_PLAYER.name()));
        assertNotNull(data.childByArg(LIBRARY_NID));
        // state data absent
        assertNull(data.childByArg(PLAYLIST_NID));
    }

    /**
     * Read data from operational datastore according to content parameter.
     */
    @Test
    void testReadDataOperationalTest() {
        final var parameters = new MultivaluedHashMap<String, String>();
        parameters.putSingle("content", "nonconfig");

        doReturn(parameters).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(OPER_JUKEBOX))).when(tx)
                .read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);

        // response must contain only operational data
        final var data = assertInstanceOf(ContainerNode.class, assertNormalizedNode(200,
            ar -> restconf.dataGET(JUKEBOX_API_PATH, uriInfo, ar)));
        // state data present
        assertNotNull(data.childByArg(CONT_PLAYER.name()));
        assertNotNull(data.childByArg(PLAYLIST_NID));

        // config data absent
        assertNull(data.childByArg(LIBRARY_NID));
    }
}

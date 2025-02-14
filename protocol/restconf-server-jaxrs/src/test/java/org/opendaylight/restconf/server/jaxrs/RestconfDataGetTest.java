/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

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
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

// FIXME: this test suite should be refactored as an AbstractDataBrokerTest without mocks:
//          - AbstractRestconfTest.restconf should be replaced with an instance wired to
//            AbstractDataBrokerTest.getDomBroker() et al.
//          - then each test case should initialize the datastores with test data
//          - then each test case should execute the request
//        if you are doing this, please structure it so that the infra can be brought down to AbstractRestconfTest and
//        reused in Netconf822Test and the like
@ExtendWith(MockitoExtension.class)
class RestconfDataGetTest extends AbstractRestconfTest {
    private static final NodeIdentifier PLAYLIST_NID = new NodeIdentifier(PLAYLIST_QNAME);
    private static final NodeIdentifier LIBRARY_NID = new NodeIdentifier(LIBRARY_QNAME);

    // config contains one child the same as in operational and one additional
    private static final ContainerNode CONFIG_JUKEBOX = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
            .withChild(CONT_PLAYER)
            .withChild(ImmutableNodes.newContainerBuilder().withNodeIdentifier(LIBRARY_NID).build())
            .build();
    // operational contains one child the same as in config and one additional
    private static final ContainerNode OPER_JUKEBOX = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
            .withChild(CONT_PLAYER)
            .withChild(ImmutableNodes.newSystemMapBuilder().withNodeIdentifier(PLAYLIST_NID).build())
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

        final var body = assertNormalizedBody(200, ar -> restconf.dataGET(JUKEBOX_API_PATH, uriInfo, sc, ar));
        assertEquals(EMPTY_JUKEBOX, body.data());
        assertFormat("""
            {
              "example-jukebox:jukebox": {
                "player": {
                  "gap": "0.2"
                }
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <jukebox xmlns="http://example.com/ns/example-jukebox">
              <player>
                <gap>0.2</gap>
              </player>
            </jukebox>""", body::formatToXML, true);
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

        final var body = assertNormalizedBody(200, ar -> restconf.dataGET(uriInfo, sc, ar));
        final var data = assertInstanceOf(ContainerNode.class, body.data());
        final var rootNodes = data.body();
        assertEquals(1, rootNodes.size());
        final var allDataChildren = assertInstanceOf(ContainerNode.class, rootNodes.iterator().next()).body();
        assertEquals(3, allDataChildren.size());

        assertFormat("""
            {
              "example-jukebox:jukebox": {
                "player": {
                  "gap": "0.2"
                }
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <data xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <jukebox xmlns="http://example.com/ns/example-jukebox">
                <library/>
                <player>
                  <gap>0.2</gap>
                </player>
              </jukebox>
            </data>""", body::formatToXML, true);
    }

    private static ContainerNode wrapNodeByDataRootContainer(final DataContainerChild data) {
        return ImmutableNodes.newContainerBuilder()
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
        doReturn(Optional.empty()).when(mountPoint).getService(DOMServerStrategy.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);

        // response must contain all child nodes from config and operational containers merged in one container
        final var body = assertNormalizedBody(200, ar -> restconf.dataGET(
            apiPath("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox"), uriInfo, sc, ar));
        final var data = assertInstanceOf(ContainerNode.class, body.data());
        assertEquals(3, data.size());
        assertNotNull(data.childByArg(CONT_PLAYER.name()));
        assertNotNull(data.childByArg(LIBRARY_NID));
        assertNotNull(data.childByArg(PLAYLIST_NID));

        assertFormat("""
            {
              "example-jukebox:jukebox": {
                "player": {
                  "gap": "0.2"
                }
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <jukebox xmlns="http://example.com/ns/example-jukebox">
              <library/>
              <player>
                <gap>0.2</gap>
              </player>
            </jukebox>""", body::formatToXML, true);
    }

    @Test
    void testReadDataNoData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(tx).read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(tx).read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);

        final var error = assertError(409, ar -> restconf.dataGET(JUKEBOX_API_PATH, uriInfo, sc, ar));
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.DATA_MISSING, error.tag());
        assertEquals(
            new ErrorMessage("Request could not be completed because the relevant data model content does not exist"),
            error.message());
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
        final var body = assertNormalizedBody(200, ar -> restconf.dataGET(JUKEBOX_API_PATH, uriInfo, sc, ar));
        final var data = assertInstanceOf(ContainerNode.class, body.data());
        // config data present
        assertNotNull(data.childByArg(CONT_PLAYER.name()));
        assertNotNull(data.childByArg(LIBRARY_NID));
        // state data absent
        assertNull(data.childByArg(PLAYLIST_NID));

        assertFormat("""
            {
              "example-jukebox:jukebox": {
                "player": {
                  "gap": "0.2"
                }
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <jukebox xmlns="http://example.com/ns/example-jukebox">
              <library/>
              <player>
                <gap>0.2</gap>
              </player>
            </jukebox>""", body::formatToXML, true);
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
        final var body = assertNormalizedBody(200, ar -> restconf.dataGET(JUKEBOX_API_PATH, uriInfo, sc, ar));
        final var data = assertInstanceOf(ContainerNode.class, body.data());
        // state data present
        assertNotNull(data.childByArg(CONT_PLAYER.name()));
        assertNotNull(data.childByArg(PLAYLIST_NID));

        // config data absent
        assertNull(data.childByArg(LIBRARY_NID));

        assertFormat("""
            {
              "example-jukebox:jukebox": {
                "player": {
                  "gap": "0.2"
                }
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <jukebox xmlns="http://example.com/ns/example-jukebox">
              <player>
                <gap>0.2</gap>
              </player>
            </jukebox>""", body::formatToXML, true);
    }

    @Test
    void readListEntry() {
        final var parameters = new MultivaluedHashMap<String, String>();
        parameters.putSingle("content", "nonconfig");

        doReturn(parameters).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "IAmN0t"))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, "IAmN0t"))
            .build()))).when(tx).read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.builder()
                .node(JUKEBOX_QNAME)
                .node(LIBRARY_QNAME)
                .node(ARTIST_QNAME)
                .nodeWithKey(ARTIST_QNAME, NAME_QNAME, "IAmN0t")
                .build());

        final var body = assertNormalizedBody(200,
            ar -> restconf.dataGET(apiPath("example-jukebox:jukebox/library/artist=IAmN0t"), uriInfo, sc, ar));
        assertFormat("""
            {
              "example-jukebox:artist": [
                {
                  "name": "IAmN0t"
                }
              ]
            }""", body::formatToJSON, true);
        assertFormat("""
            <artist xmlns="http://example.com/ns/example-jukebox">
              <name>IAmN0t</name>
            </artist>""", body::formatToXML, true);
    }
}

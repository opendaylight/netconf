/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.CREATE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.DELETE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REMOVE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REPLACE;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfDataServiceImplTest extends AbstractJukeboxTest {
    private ContainerNode buildBaseCont;
    private ContainerNode buildBaseContConfig;
    private ContainerNode buildBaseContOperational;
    private YangInstanceIdentifier iidBase;
    private RestconfDataServiceImpl dataService;
    private ContainerNode buildPlayerCont;
    private ContainerNode buildLibraryCont;
    private MapNode buildPlaylistList;

    @Mock
    private DOMTransactionChain domTransactionChain;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private DOMDataTreeWriteTransaction write;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private DOMDataBroker mountDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;
    @Mock
    private DOMActionService actionService;
    @Mock
    private RestconfStreamsSubscriptionService delegRestconfSubscrService;
    @Mock
    private MultivaluedMap<String, String> queryParamenters;
    @Mock
    private AsyncResponse asyncResponse;

    @Before
    public void setUp() throws Exception {
        doReturn(Set.of()).when(queryParamenters).entrySet();
        doReturn(queryParamenters).when(uriInfo).getQueryParameters();

        buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(PLAYER_QNAME))
                .withChild(GAP_LEAF)
                .build();

        buildLibraryCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIBRARY_QNAME))
                .build();

        buildPlaylistList = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
                .build();

        buildBaseCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
                .withChild(buildPlayerCont)
                .build();

        // config contains one child the same as in operational and one additional
        buildBaseContConfig = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
                .withChild(buildPlayerCont)
                .withChild(buildLibraryCont)
                .build();

        // operational contains one child the same as in config and one additional
        buildBaseContOperational = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
                .withChild(buildPlayerCont)
                .withChild(buildPlaylistList)
                .build();

        iidBase = YangInstanceIdentifier.of(JUKEBOX_QNAME);

        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        DOMDataBroker mockDataBroker = mock(DOMDataBroker.class);
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();

        dataService = new RestconfDataServiceImpl(() -> DatabindContext.ofModel(JUKEBOX_SCHEMA), mockDataBroker,
                mountPointService, delegRestconfSubscrService, actionService, new StreamsConfiguration(0, 1, 0, false));
        doReturn(Optional.of(mountPoint)).when(mountPointService)
                .getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(FixedDOMSchemaService.of(JUKEBOX_SCHEMA))).when(mountPoint)
                .getService(DOMSchemaService.class);
        doReturn(Optional.of(mountDataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(read).when(mountDataBroker).newReadOnlyTransaction();
        doReturn(readWrite).when(mountDataBroker).newReadWriteTransaction();
    }

    @Test
    public void testReadData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(buildBaseCont))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(read).read(LogicalDatastoreType.OPERATIONAL, iidBase);
        final Response response = dataService.readData("example-jukebox:jukebox", uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        assertEquals(buildBaseCont, ((NormalizedNodePayload) response.getEntity()).getData());
    }

    @Test
    public void testReadRootData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(wrapNodeByDataRootContainer(buildBaseContConfig))))
                .when(read)
                .read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        doReturn(immediateFluentFuture(Optional.of(wrapNodeByDataRootContainer(buildBaseContOperational))))
                .when(read)
                .read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of());
        final Response response = dataService.readData(uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());

        final NormalizedNode data = ((NormalizedNodePayload) response.getEntity()).getData();
        assertTrue(data instanceof ContainerNode);
        final Collection<DataContainerChild> rootNodes = ((ContainerNode) data).body();
        assertEquals(1, rootNodes.size());
        final Collection<DataContainerChild> allDataChildren = ((ContainerNode) rootNodes.iterator().next()).body();
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
    public void testReadDataMountPoint() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(buildBaseContConfig))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateFluentFuture(Optional.of(buildBaseContOperational))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, iidBase);

        final Response response = dataService.readData(
                "example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox", uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain all child nodes from config and operational containers merged in one container
        final NormalizedNode data = ((NormalizedNodePayload) response.getEntity()).getData();
        assertTrue(data instanceof ContainerNode);
        assertEquals(3, ((ContainerNode) data).size());
        assertNotNull(((ContainerNode) data).childByArg(buildPlayerCont.name()));
        assertNotNull(((ContainerNode) data).childByArg(buildLibraryCont.name()));
        assertNotNull(((ContainerNode) data).childByArg(buildPlaylistList.name()));
    }

    @Test
    public void testReadDataNoData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(read).read(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(read).read(LogicalDatastoreType.OPERATIONAL, iidBase);

        final var errors = assertThrows(RestconfDocumentedException.class,
            () -> dataService.readData("example-jukebox:jukebox", uriInfo)).getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals("Request could not be completed because the relevant data model content does not exist",
            error.getErrorMessage());
    }

    /**
     * Read data from config datastore according to content parameter.
     */
    @Test
    public void testReadDataConfigTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put("content", List.of("config"));

        doReturn(parameters).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(buildBaseContConfig))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, iidBase);

        final Response response = dataService.readData("example-jukebox:jukebox", uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain only config data
        final NormalizedNode data = ((NormalizedNodePayload) response.getEntity()).getData();

        // config data present
        assertNotNull(((ContainerNode) data).childByArg(buildPlayerCont.name()));
        assertNotNull(((ContainerNode) data).childByArg(buildLibraryCont.name()));

        // state data absent
        assertNull(((ContainerNode) data).childByArg(buildPlaylistList.name()));
    }

    /**
     * Read data from operational datastore according to content parameter.
     */
    @Test
    public void testReadDataOperationalTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put("content", List.of("nonconfig"));

        doReturn(parameters).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(buildBaseContOperational))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, iidBase);

        final Response response = dataService.readData("example-jukebox:jukebox", uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain only operational data
        final NormalizedNode data = ((NormalizedNodePayload) response.getEntity()).getData();

        // state data present
        assertNotNull(((ContainerNode) data).childByArg(buildPlayerCont.name()));
        assertNotNull(((ContainerNode) data).childByArg(buildPlaylistList.name()));

        // config data absent
        assertNull(((ContainerNode) data).childByArg(buildLibraryCont.name()));
    }

    @Test
    public void testPutData() {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iidBase);
        final NormalizedNodePayload payload = NormalizedNodePayload.of(iidContext, buildBaseCont);

        doReturn(immediateTrueFluentFuture()).when(read)
                .exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, iidBase, payload.getData());
        final Response response = dataService.putData(null, payload, uriInfo);
        assertNotNull(response);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPutDataWithMountPoint() {
        final InstanceIdentifierContext iidContext =
            InstanceIdentifierContext.ofMountPointPath(mountPoint, JUKEBOX_SCHEMA, iidBase);
        final NormalizedNodePayload payload = NormalizedNodePayload.of(iidContext, buildBaseCont);

        doReturn(immediateTrueFluentFuture()).when(read)
                .exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, iidBase, payload.getData());
        final Response response = dataService.putData(null, payload, uriInfo);
        assertNotNull(response);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPostData() {
        final var identifier = NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band");
        final var entryNode = Builders.mapEntryBuilder()
            .withNodeIdentifier(identifier)
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band"))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description"))
            .build();

        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        final var node = iidBase.node(identifier);
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        final var response = dataService.postData(NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iidBase),
            Builders.mapBuilder().withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME)).withChild(entryNode).build()),
            uriInfo);
        assertEquals(201, response.getStatus());
    }

    @Test
    public void testDeleteData() {
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateTrueFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        final var captor = ArgumentCaptor.forClass(Response.class);
        doReturn(true).when(asyncResponse).resume(captor.capture());
        dataService.deleteData("example-jukebox:jukebox", asyncResponse);

        assertEquals(204, captor.getValue().getStatus());
    }

    @Test
    public void testDeleteDataNotExisting() {
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        final var captor = ArgumentCaptor.forClass(RestconfDocumentedException.class);
        doReturn(true).when(asyncResponse).resume(captor.capture());
        dataService.deleteData("example-jukebox:jukebox", asyncResponse);

        final var errors = captor.getValue().getErrors();
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
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateTrueFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        final var captor = ArgumentCaptor.forClass(Response.class);
        doReturn(true).when(asyncResponse).resume(captor.capture());
        dataService.deleteData("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox", asyncResponse);

        assertEquals(204, captor.getValue().getStatus());
    }

    @Test
    public void testPatchData() {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iidBase);
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(iidBase)
                .node(PLAYER_QNAME)
                .node(GAP_QNAME)
                .build();
        final PatchContext patch = new PatchContext(iidContext, List.of(
            new PatchEntity("create data", CREATE, iidBase, buildBaseCont),
            new PatchEntity("replace data", REPLACE, iidBase, buildBaseCont),
            new PatchEntity("delete data", DELETE, iidleaf)), "test patch id");

        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateTrueFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);
        final PatchStatusContext status = dataService.patchData(patch, uriInfo);
        assertTrue(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertEquals("replace data", status.getEditCollection().get(1).getEditId());
    }

    @Test
    public void testPatchDataMountPoint() throws Exception {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofMountPointPath(mountPoint,
            JUKEBOX_SCHEMA, iidBase);
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(iidBase)
                .node(PLAYER_QNAME)
                .node(GAP_QNAME)
                .build();
        final PatchContext patch = new PatchContext(iidContext, List.of(
            new PatchEntity("create data", CREATE, iidBase, buildBaseCont),
            new PatchEntity("replace data", REPLACE, iidBase, buildBaseCont),
            new PatchEntity("delete data", DELETE, iidleaf)), "test patch id");

        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateTrueFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);

        final PatchStatusContext status = dataService.patchData(patch, uriInfo);
        assertTrue(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertNull(status.getGlobalErrors());
    }

    @Test
    public void testPatchDataDeleteNotExist() {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, iidBase);
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(iidBase)
                .node(PLAYER_QNAME)
                .node(GAP_QNAME)
                .build();
        final PatchContext patch = new PatchContext(iidContext, List.of(
            new PatchEntity("create data", CREATE, iidBase, buildBaseCont),
            new PatchEntity("remove data", REMOVE, iidleaf),
            new PatchEntity("delete data", DELETE, iidleaf)), "test patch id");

        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(true).when(readWrite).cancel();
        final PatchStatusContext status = dataService.patchData(patch, uriInfo);

        assertFalse(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertTrue(status.getEditCollection().get(0).isOk());
        assertTrue(status.getEditCollection().get(1).isOk());
        assertFalse(status.getEditCollection().get(2).isOk());
        assertFalse(status.getEditCollection().get(2).getEditErrors().isEmpty());
        final String errorMessage = status.getEditCollection().get(2).getEditErrors().get(0).getErrorMessage();
        assertEquals("Data does not exist", errorMessage);
    }

    @Test
    public void testGetRestconfStrategy() {
        RestconfStrategy restconfStrategy = dataService.getRestconfStrategy(mountPoint);
        assertTrue(restconfStrategy instanceof MdsalRestconfStrategy);

        doReturn(Optional.of(netconfService)).when(mountPoint).getService(NetconfDataTreeService.class);
        restconfStrategy = dataService.getRestconfStrategy(mountPoint);
        assertTrue(restconfStrategy instanceof NetconfRestconfStrategy);
    }

    @Test
    public void testValidInputData() {
        RestconfDataServiceImpl.validInputData(true, NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID), GAP_LEAF));
    }

    @Test
    public void testValidTopLevelNodeName() {
        RestconfDataServiceImpl.validTopLevelNodeName(GAP_IID, NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID), GAP_LEAF));
        RestconfDataServiceImpl.validTopLevelNodeName(JUKEBOX_IID, NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, JUKEBOX_IID), EMPTY_JUKEBOX));
    }

    @Test
    public void testValidTopLevelNodeNamePathEmpty() {
        final var iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID);
        final var payload = NormalizedNodePayload.of(iidContext, GAP_LEAF);

        // FIXME: more asserts
        assertThrows(RestconfDocumentedException.class,
            () -> RestconfDataServiceImpl.validTopLevelNodeName(YangInstanceIdentifier.of(), payload));
    }

    @Test
    public void testValidTopLevelNodeNameWrongTopIdentifier() {
        final var iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID);
        final var payload = NormalizedNodePayload.of(iidContext, GAP_LEAF);

        // FIXME: more asserts
        assertThrows(RestconfDocumentedException.class,
            () -> RestconfDataServiceImpl.validTopLevelNodeName(GAP_IID.getAncestor(1), payload));
    }

    @Test
    public void testValidateListKeysEqualityInPayloadAndUri() {
        final var iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, YangInstanceIdentifier.builder()
            .node(JUKEBOX_QNAME)
            .node(PLAYLIST_QNAME)
            .nodeWithKey(PLAYLIST_QNAME, NAME_QNAME, "name of band")
            .build());
        final NormalizedNodePayload payload = NormalizedNodePayload.of(iidContext, BAND_ENTRY);
        RestconfDataServiceImpl.validateListKeysEqualityInPayloadAndUri(payload);
    }
}

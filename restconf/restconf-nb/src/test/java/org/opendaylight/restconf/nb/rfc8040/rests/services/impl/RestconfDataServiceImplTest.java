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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfDataServiceImplTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";

    private ContainerNode buildBaseCont;
    private ContainerNode buildBaseContConfig;
    private ContainerNode buildBaseContOperational;
    private EffectiveModelContext contextRef;
    private YangInstanceIdentifier iidBase;
    private RestconfDataServiceImpl dataService;
    private QName baseQName;
    private QName containerPlayerQname;
    private QName leafQname;
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

    @Before
    public void setUp() throws Exception {
        doReturn(Set.of()).when(queryParamenters).entrySet();
        doReturn(queryParamenters).when(uriInfo).getQueryParameters();

        baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        containerPlayerQname = QName.create(baseQName, "player");
        leafQname = QName.create(baseQName, "gap");

        final QName containerLibraryQName = QName.create(baseQName, "library");
        final QName listPlaylistQName = QName.create(baseQName, "playlist");

        final LeafNode<?> buildLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(leafQname))
                .withValue(0.2)
                .build();

        buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(containerPlayerQname))
                .withChild(buildLeaf)
                .build();

        buildLibraryCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(containerLibraryQName))
                .build();

        buildPlaylistList = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(listPlaylistQName))
                .build();

        buildBaseCont = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(baseQName))
                .withChild(buildPlayerCont)
                .build();

        // config contains one child the same as in operational and one additional
        buildBaseContConfig = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(baseQName))
                .withChild(buildPlayerCont)
                .withChild(buildLibraryCont)
                .build();

        // operational contains one child the same as in config and one additional
        buildBaseContOperational = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(baseQName))
                .withChild(buildPlayerCont)
                .withChild(buildPlaylistList)
                .build();

        iidBase = YangInstanceIdentifier.builder()
                .node(baseQName)
                .build();

        contextRef = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));

        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        DOMDataBroker mockDataBroker = mock(DOMDataBroker.class);
        doReturn(read).when(mockDataBroker).newReadOnlyTransaction();
        doReturn(readWrite).when(mockDataBroker).newReadWriteTransaction();

        dataService = new RestconfDataServiceImpl(() -> DatabindContext.ofModel(contextRef), mockDataBroker,
                mountPointService, delegRestconfSubscrService, actionService, new StreamsConfiguration(0, 1, 0, false));
        doReturn(Optional.of(mountPoint)).when(mountPointService)
                .getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(FixedDOMSchemaService.of(contextRef))).when(mountPoint)
                .getService(DOMSchemaService.class);
        doReturn(Optional.of(mountDataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(read).when(mountDataBroker).newReadOnlyTransaction();
        doReturn(readWrite).when(mountDataBroker).newReadWriteTransaction();
    }

    @Test
    public void testReadData() {
        doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
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
        doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
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
        doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
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
        assertTrue(((ContainerNode) data).findChildByArg(buildPlayerCont.getIdentifier()).isPresent());
        assertTrue(((ContainerNode) data).findChildByArg(buildLibraryCont.getIdentifier()).isPresent());
        assertTrue(((ContainerNode) data).findChildByArg(buildPlaylistList.getIdentifier()).isPresent());
    }

    @Test
    public void testReadDataNoData() {
        doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
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
        assertTrue(((ContainerNode) data).findChildByArg(buildPlayerCont.getIdentifier()).isPresent());
        assertTrue(((ContainerNode) data).findChildByArg(buildLibraryCont.getIdentifier()).isPresent());

        // state data absent
        assertFalse(((ContainerNode) data).findChildByArg(buildPlaylistList.getIdentifier()).isPresent());
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
        assertTrue(((ContainerNode) data).findChildByArg(buildPlayerCont.getIdentifier()).isPresent());
        assertTrue(((ContainerNode) data).findChildByArg(buildPlaylistList.getIdentifier()).isPresent());

        // config data absent
        assertFalse(((ContainerNode) data).findChildByArg(buildLibraryCont.getIdentifier()).isPresent());
    }

    @Test
    public void testPutData() {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(contextRef, iidBase);
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
            InstanceIdentifierContext.ofMountPointPath(mountPoint, contextRef, iidBase);
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
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");
        final NodeIdentifierWithPredicates nodeWithKey =
                NodeIdentifierWithPredicates.of(listQname, listKeyQname, "name of band");

        doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(contextRef, iidBase);
        final NormalizedNodePayload payload = NormalizedNodePayload.of(iidContext, Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(listQname))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(ImmutableNodes.leafNode(QName.create(baseQName, "name"), "name of band"))
                .withChild(ImmutableNodes.leafNode(QName.create(baseQName, "description"), "band description"))
                .build())
            .build());
        final MapNode data = (MapNode) payload.getData();
        final MapEntryNode entryNode = data.body().iterator().next();
        final NodeIdentifierWithPredicates identifier = entryNode.getIdentifier();
        final YangInstanceIdentifier node =
                payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, entryNode);
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        final Response response = dataService.postData(null, payload, uriInfo);
        assertEquals(201, response.getStatus());
    }

    @Test
    public void testDeleteData() {
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateTrueFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        final Response response = dataService.deleteData("example-jukebox:jukebox");
        assertNotNull(response);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    /**
     * Test of deleting data on mount point.
     */
    @Test
    public void testDeleteDataMountPoint() {
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(immediateTrueFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        final Response response =
                dataService.deleteData("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox");
        assertNotNull(response);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPatchData() {
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(contextRef, iidBase);
        final List<PatchEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(iidBase)
                .node(containerPlayerQname)
                .node(leafQname)
                .build();
        entity.add(new PatchEntity("create data", CREATE, iidBase, buildBaseCont));
        entity.add(new PatchEntity("replace data", REPLACE, iidBase, buildBaseCont));
        entity.add(new PatchEntity("delete data", DELETE, iidleaf));
        final PatchContext patch = new PatchContext(iidContext, entity, "test patch id");

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
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofMountPointPath(mountPoint, contextRef,
                iidBase);
        final List<PatchEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(iidBase)
                .node(containerPlayerQname)
                .node(leafQname)
                .build();
        entity.add(new PatchEntity("create data", CREATE, iidBase, buildBaseCont));
        entity.add(new PatchEntity("replace data", REPLACE, iidBase, buildBaseCont));
        entity.add(new PatchEntity("delete data", DELETE, iidleaf));
        final PatchContext patch = new PatchContext(iidContext, entity, "test patch id");

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
        final InstanceIdentifierContext iidContext = InstanceIdentifierContext.ofLocalPath(contextRef, iidBase);
        final List<PatchEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(iidBase)
                .node(containerPlayerQname)
                .node(leafQname)
                .build();
        entity.add(new PatchEntity("create data", CREATE, iidBase, buildBaseCont));
        entity.add(new PatchEntity("remove data", REMOVE, iidleaf));
        entity.add(new PatchEntity("delete data", DELETE, iidleaf));
        final PatchContext patch = new PatchContext(iidContext, entity, "test patch id");

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
}

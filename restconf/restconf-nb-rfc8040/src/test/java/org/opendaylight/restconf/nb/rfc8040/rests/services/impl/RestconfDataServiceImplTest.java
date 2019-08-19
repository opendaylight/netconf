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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.CREATE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.DELETE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REMOVE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REPLACE;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfDataServiceImplTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";

    private ContainerNode buildBaseCont;
    private ContainerNode buildBaseContConfig;
    private ContainerNode buildBaseContOperational;
    private SchemaContextRef contextRef;
    private YangInstanceIdentifier iidBase;
    private DataSchemaNode schemaNode;
    private RestconfDataServiceImpl dataService;
    private QName baseQName;
    private QName containerPlayerQname;
    private QName leafQname;
    private ContainerNode buildPlayerCont;
    private ContainerNode buildLibraryCont;
    private MapNode buildPlaylistList;
    private TransactionChainHandler transactionChainHandler;

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
    private DOMTransactionChain mountTransactionChain;
    @Mock
    private RestconfStreamsSubscriptionService delegRestconfSubscrService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        final MultivaluedMap<String, String> value = Mockito.mock(MultivaluedMap.class);
        Mockito.when(value.entrySet()).thenReturn(new HashSet<>());
        Mockito.when(this.uriInfo.getQueryParameters()).thenReturn(value);

        this.baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        this.containerPlayerQname = QName.create(this.baseQName, "player");
        this.leafQname = QName.create(this.baseQName, "gap");

        final QName containerLibraryQName = QName.create(this.baseQName, "library");
        final QName listPlaylistQName = QName.create(this.baseQName, "playlist");

        final LeafNode buildLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(this.leafQname))
                .withValue(0.2)
                .build();

        this.buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(this.containerPlayerQname))
                .withChild(buildLeaf)
                .build();

        this.buildLibraryCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerLibraryQName))
                .build();

        this.buildPlaylistList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(listPlaylistQName))
                .build();

        this.buildBaseCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(this.baseQName))
                .withChild(this.buildPlayerCont)
                .build();

        // config contains one child the same as in operational and one additional
        this.buildBaseContConfig = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(this.baseQName))
                .withChild(this.buildPlayerCont)
                .withChild(this.buildLibraryCont)
                .build();

        // operational contains one child the same as in config and one additional
        this.buildBaseContOperational = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(this.baseQName))
                .withChild(this.buildPlayerCont)
                .withChild(this.buildPlaylistList)
                .build();

        this.iidBase = YangInstanceIdentifier.builder()
                .node(this.baseQName)
                .build();

        this.contextRef = new SchemaContextRef(
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT)));
        this.schemaNode = DataSchemaContextTree.from(this.contextRef.get()).getChild(this.iidBase).getDataSchemaNode();

        doReturn(CommitInfo.emptyFluentFuture()).when(this.write).commit();
        doReturn(CommitInfo.emptyFluentFuture()).when(this.readWrite).commit();

        doReturn(this.read).when(domTransactionChain).newReadOnlyTransaction();
        doReturn(this.readWrite).when(domTransactionChain).newReadWriteTransaction();
        doReturn(this.write).when(domTransactionChain).newWriteOnlyTransaction();

        DOMDataBroker mockDataBroker = Mockito.mock(DOMDataBroker.class);
        Mockito.doReturn(domTransactionChain).when(mockDataBroker).createTransactionChain(Mockito.any());

        transactionChainHandler = new TransactionChainHandler(mockDataBroker);

        final SchemaContextHandler schemaContextHandler = SchemaContextHandler.newInstance(transactionChainHandler,
                Mockito.mock(DOMSchemaService.class));

        schemaContextHandler.onGlobalContextUpdated(this.contextRef.get());
        this.dataService = new RestconfDataServiceImpl(schemaContextHandler, this.transactionChainHandler,
                DOMMountPointServiceHandler.newInstance(mountPointService), this.delegRestconfSubscrService);
        doReturn(Optional.of(this.mountPoint)).when(this.mountPointService)
                .getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(this.contextRef.get()).when(this.mountPoint).getSchemaContext();
        doReturn(Optional.of(this.mountDataBroker)).when(this.mountPoint).getService(DOMDataBroker.class);
        doReturn(this.mountTransactionChain).when(this.mountDataBroker)
                .createTransactionChain(any(DOMTransactionChainListener.class));
        doReturn(this.read).when(this.mountTransactionChain).newReadOnlyTransaction();
        doReturn(this.readWrite).when(this.mountTransactionChain).newReadWriteTransaction();
    }

    @Test
    public void testReadData() {
        doReturn(new MultivaluedHashMap<String, String>()).when(this.uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(this.read).read(LogicalDatastoreType.OPERATIONAL, this.iidBase);
        final Response response = this.dataService.readData("example-jukebox:jukebox", this.uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        assertEquals(this.buildBaseCont, ((NormalizedNodeContext) response.getEntity()).getData());
    }

    @Test
    public void testReadRootData() {
        doReturn(new MultivaluedHashMap<String, String>()).when(this.uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(wrapNodeByDataRootContainer(this.buildBaseContConfig))))
                .when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY);
        doReturn(immediateFluentFuture(Optional.of(wrapNodeByDataRootContainer(this.buildBaseContOperational))))
                .when(this.read)
                .read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.EMPTY);
        final Response response = this.dataService.readData(this.uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());

        final NormalizedNode<?, ?> data = ((NormalizedNodeContext) response.getEntity()).getData();
        assertTrue(data instanceof ContainerNode);
        final Collection<DataContainerChild<? extends PathArgument, ?>> rootNodes = ((ContainerNode) data).getValue();
        assertEquals(1, rootNodes.size());
        final Collection<DataContainerChild<? extends PathArgument, ?>> allDataChildren
                = ((ContainerNode) rootNodes.iterator().next()).getValue();
        assertEquals(3, allDataChildren.size());
    }

    private static ContainerNode wrapNodeByDataRootContainer(final DataContainerChild<?, ?> data) {
        return ImmutableContainerNodeBuilder.create()
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
        doReturn(new MultivaluedHashMap<String, String>()).when(this.uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(this.buildBaseContConfig))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateFluentFuture(Optional.of(this.buildBaseContOperational))).when(this.read)
                .read(LogicalDatastoreType.OPERATIONAL, this.iidBase);

        final Response response = this.dataService.readData(
                "example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox", this.uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain all child nodes from config and operational containers merged in one container
        final NormalizedNode<?, ?> data = ((NormalizedNodeContext) response.getEntity()).getData();
        assertTrue(data instanceof ContainerNode);
        assertEquals(3, ((ContainerNode) data).getValue().size());
        assertTrue(((ContainerNode) data).getChild(this.buildPlayerCont.getIdentifier()).isPresent());
        assertTrue(((ContainerNode) data).getChild(this.buildLibraryCont.getIdentifier()).isPresent());
        assertTrue(((ContainerNode) data).getChild(this.buildPlaylistList.getIdentifier()).isPresent());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testReadDataNoData() {
        doReturn(new MultivaluedHashMap<String, String>()).when(this.uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(this.read).read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(this.read).read(LogicalDatastoreType.OPERATIONAL, this.iidBase);
        this.dataService.readData("example-jukebox:jukebox", this.uriInfo);
    }

    /**
     * Read data from config datastore according to content parameter.
     */
    @Test
    public void testReadDataConfigTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put("content", Collections.singletonList("config"));

        doReturn(parameters).when(this.uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(this.buildBaseContConfig))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateFluentFuture(Optional.of(this.buildBaseContOperational))).when(this.read)
                .read(LogicalDatastoreType.OPERATIONAL, this.iidBase);

        final Response response = this.dataService.readData("example-jukebox:jukebox", this.uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain only config data
        final NormalizedNode<?, ?> data = ((NormalizedNodeContext) response.getEntity()).getData();

        // config data present
        assertTrue(((ContainerNode) data).getChild(this.buildPlayerCont.getIdentifier()).isPresent());
        assertTrue(((ContainerNode) data).getChild(this.buildLibraryCont.getIdentifier()).isPresent());

        // state data absent
        assertFalse(((ContainerNode) data).getChild(this.buildPlaylistList.getIdentifier()).isPresent());
    }

    /**
     * Read data from operational datastore according to content parameter.
     */
    @Test
    public void testReadDataOperationalTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put("content", Collections.singletonList("nonconfig"));

        doReturn(parameters).when(this.uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(this.buildBaseContConfig))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateFluentFuture(Optional.of(this.buildBaseContOperational))).when(this.read)
                .read(LogicalDatastoreType.OPERATIONAL, this.iidBase);

        final Response response = this.dataService.readData("example-jukebox:jukebox", this.uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain only operational data
        final NormalizedNode<?, ?> data = ((NormalizedNodeContext) response.getEntity()).getData();

        // state data present
        assertTrue(((ContainerNode) data).getChild(this.buildPlayerCont.getIdentifier()).isPresent());
        assertTrue(((ContainerNode) data).getChild(this.buildPlaylistList.getIdentifier()).isPresent());

        // config data absent
        assertFalse(((ContainerNode) data).getChild(this.buildLibraryCont.getIdentifier()).isPresent());
    }

    @Test
    public void testPutData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidBase, this.schemaNode, null, this.contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(immediateTrueFluentFuture()).when(this.readWrite)
                .exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, payload.getData());
        final Response response = this.dataService.putData(null, payload, this.uriInfo);
        assertNotNull(response);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPutDataWithMountPoint() {
//        final DOMDataBroker dataBroker = Mockito.mock(DOMDataBroker.class);
//        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
//        doReturn(this.transactionChainHandler.get()).when(dataBroker)
//                .createTransactionChain(RestConnectorProvider.TRANSACTION_CHAIN_LISTENER);
        final InstanceIdentifierContext<DataSchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidBase, this.schemaNode, mountPoint, this.contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(immediateTrueFluentFuture()).when(this.readWrite)
                .exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, payload.getData());
        final Response response = this.dataService.putData(null, payload, this.uriInfo);
        assertNotNull(response);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPostData() {
        final QName listQname = QName.create(this.baseQName, "playlist");
        final QName listKeyQname = QName.create(this.baseQName, "name");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQname, "name of band");
        final LeafNode<Object> content = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(this.baseQName, "name")))
                .withValue("name of band")
                .build();
        final LeafNode<Object> content2 = Builders.leafBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(this.baseQName, "description")))
            .withValue("band description")
            .build();
        final MapEntryNode mapEntryNode = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(content)
                .withChild(content2)
                .build();
        final MapNode buildList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(listQname))
                .withChild(mapEntryNode)
                .build();

        doReturn(new MultivaluedHashMap<String, String>()).when(this.uriInfo).getQueryParameters();
        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidBase, null, null, this.contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, buildList);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(this.read).read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        final MapNode data = (MapNode) payload.getData();
        final YangInstanceIdentifier.NodeIdentifierWithPredicates identifier =
                data.getValue().iterator().next().getIdentifier();
        final YangInstanceIdentifier node =
                payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, node, payload.getData());
        doReturn(UriBuilder.fromUri("http://localhost:8181/restconf/15/")).when(this.uriInfo).getBaseUriBuilder();

        final Response response = this.dataService.postData(null, payload, this.uriInfo);
        assertEquals(201, response.getStatus());
    }

    @Test
    public void testDeleteData() {
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateTrueFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        final Response response = this.dataService.deleteData("example-jukebox:jukebox");
        assertNotNull(response);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    /**
     * Test of deleting data on mount point.
     */
    @Test
    public void testDeleteDataMountPoint() {
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateTrueFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        final Response response =
                this.dataService.deleteData("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox");
        assertNotNull(response);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPatchData() throws Exception {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidBase, this.schemaNode, null, this.contextRef.get());
        final List<PatchEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(this.iidBase)
                .node(this.containerPlayerQname)
                .node(this.leafQname)
                .build();
        entity.add(new PatchEntity("create data", CREATE, this.iidBase, this.buildBaseCont));
        entity.add(new PatchEntity("replace data", REPLACE, this.iidBase, this.buildBaseCont));
        entity.add(new PatchEntity("delete data", DELETE, iidleaf));
        final PatchContext patch = new PatchContext(iidContext, entity, "test patch id");

        doReturn(immediateFluentFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, this.buildBaseCont);
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateTrueFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);
        final PatchStatusContext status = this.dataService.patchData(patch, this.uriInfo);
        assertTrue(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertEquals("replace data", status.getEditCollection().get(1).getEditId());
    }

    @Test
    public void testPatchDataMountPoint() throws Exception {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(
                this.iidBase, this.schemaNode, this.mountPoint, this.contextRef.get());
        final List<PatchEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(this.iidBase)
                .node(this.containerPlayerQname)
                .node(this.leafQname)
                .build();
        entity.add(new PatchEntity("create data", CREATE, this.iidBase, this.buildBaseCont));
        entity.add(new PatchEntity("replace data", REPLACE, this.iidBase, this.buildBaseCont));
        entity.add(new PatchEntity("delete data", DELETE, iidleaf));
        final PatchContext patch = new PatchContext(iidContext, entity, "test patch id");

        doReturn(immediateFluentFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, this.buildBaseCont);
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateTrueFluentFuture()).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);

        final PatchStatusContext status = this.dataService.patchData(patch, this.uriInfo);
        assertTrue(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertNull(status.getGlobalErrors());
    }

    @Test
    public void testPatchDataDeleteNotExist() throws Exception {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext =
                new InstanceIdentifierContext<>(this.iidBase, this.schemaNode, null, this.contextRef.get());
        final List<PatchEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(this.iidBase)
                .node(this.containerPlayerQname)
                .node(this.leafQname)
                .build();
        entity.add(new PatchEntity("create data", CREATE, this.iidBase, this.buildBaseCont));
        entity.add(new PatchEntity("remove data", REMOVE, iidleaf));
        entity.add(new PatchEntity("delete data", DELETE, iidleaf));
        final PatchContext patch = new PatchContext(iidContext, entity, "test patch id");

        doReturn(immediateFluentFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, this.buildBaseCont);
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(immediateFalseFluentFuture())
                .when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(true).when(this.readWrite).cancel();
        final PatchStatusContext status = this.dataService.patchData(patch, this.uriInfo);

        assertFalse(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertTrue(status.getEditCollection().get(0).isOk());
        assertTrue(status.getEditCollection().get(1).isOk());
        assertFalse(status.getEditCollection().get(2).isOk());
        assertFalse(status.getEditCollection().get(2).getEditErrors().isEmpty());
        final String errorMessage = status.getEditCollection().get(2).getEditErrors().get(0).getErrorMessage();
        assertEquals("Data does not exist", errorMessage);
    }
}

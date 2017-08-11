/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.restful.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

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

    @Mock
    private TransactionChainHandler transactionChainHandler;
    @Mock
    private DOMTransactionChain domTransactionChain;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private DOMDataReadWriteTransaction readWrite;
    @Mock
    private DOMDataReadOnlyTransaction read;
    @Mock
    private DOMDataWriteTransaction write;
    @Mock
    private DOMMountPointServiceHandler mountPointServiceHandler;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private DOMDataBroker mountDataBroker;
    @Mock
    private DOMTransactionChain transactionChain;
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

        this.contextRef = new SchemaContextRef(TestRestconfUtils.loadSchemaContext(PATH_FOR_NEW_SCHEMA_CONTEXT));
        this.schemaNode = DataSchemaContextTree.from(this.contextRef.get()).getChild(this.iidBase).getDataSchemaNode();

        final TransactionChainHandler txHandler = Mockito.mock(TransactionChainHandler.class);
        final DOMTransactionChain domTx = Mockito.mock(DOMTransactionChain.class);
        Mockito.when(txHandler.get()).thenReturn(domTx);
        final DOMDataWriteTransaction wTx = Mockito.mock(DOMDataWriteTransaction.class);
        Mockito.when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        final CheckedFuture checked = Mockito.mock(CheckedFuture.class);
        Mockito.when(wTx.submit()).thenReturn(checked);
        final Object valueObj = null;
        Mockito.when(checked.checkedGet()).thenReturn(valueObj);
        final SchemaContextHandler schemaContextHandler = new SchemaContextHandler(txHandler);

        schemaContextHandler.onGlobalContextUpdated(this.contextRef.get());
        this.dataService = new RestconfDataServiceImpl(schemaContextHandler, this.transactionChainHandler,
                this.mountPointServiceHandler, this.delegRestconfSubscrService);
        doReturn(this.domTransactionChain).when(this.transactionChainHandler).get();
        doReturn(this.read).when(this.domTransactionChain).newReadOnlyTransaction();
        doReturn(this.readWrite).when(this.domTransactionChain).newReadWriteTransaction();
        doReturn(this.write).when(this.domTransactionChain).newWriteOnlyTransaction();
        doReturn(this.mountPointService).when(this.mountPointServiceHandler).get();
        doReturn(Optional.of(this.mountPoint)).when(this.mountPointService).getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(this.contextRef.get()).when(this.mountPoint).getSchemaContext();
        doReturn(Optional.of(this.mountDataBroker)).when(this.mountPoint).getService(DOMDataBroker.class);
        doReturn(this.transactionChain).when(this.mountDataBroker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(this.read).when(this.transactionChain).newReadOnlyTransaction();
        doReturn(this.readWrite).when(this.transactionChain).newReadWriteTransaction();
    }

    @Test
    public void testReadData() {
        doReturn(new MultivaluedHashMap<String, String>()).when(this.uriInfo).getQueryParameters();
        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(this.read).read(LogicalDatastoreType.OPERATIONAL, this.iidBase);
        final Response response = this.dataService.readData("example-jukebox:jukebox", this.uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        assertEquals(this.buildBaseCont, ((NormalizedNodeContext) response.getEntity()).getData());
    }

    /**
     * Test read data from mount point when both {@link LogicalDatastoreType#CONFIGURATION} and
     * {@link LogicalDatastoreType#OPERATIONAL} contains the same data and some additional data to be merged.
     */
    @Test
    public void testReadDataMountPoint() {
        doReturn(new MultivaluedHashMap<String, String>()).when(this.uriInfo).getQueryParameters();
        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseContConfig))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseContOperational))).when(this.read)
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
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(this.read).read(LogicalDatastoreType.CONFIGURATION,
                this.iidBase);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(this.read).read(LogicalDatastoreType.OPERATIONAL,
                this.iidBase);
        this.dataService.readData("example-jukebox:jukebox", this.uriInfo);
    }

    /**
     * Read data from config datastore according to content parameter
     */
    @Test
    public void testReadDataConfigTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put("content", Collections.singletonList("config"));

        doReturn(parameters).when(this.uriInfo).getQueryParameters();
        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseContConfig))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseContOperational))).when(this.read)
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
     * Read data from operational datastore according to content parameter
     */
    @Test
    public void testReadDataOperationalTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put("content", Collections.singletonList("nonconfig"));

        doReturn(parameters).when(this.uriInfo).getQueryParameters();
        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseContConfig))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseContOperational))).when(this.read)
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
        final InstanceIdentifierContext<DataSchemaNode> iidContext = new InstanceIdentifierContext<>(this.iidBase, this.schemaNode, null, this.contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        final Response response = this.dataService.putData(null, payload, this.uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
    }

    @Ignore
    @Test
    public void testPutDataWithMountPoint() {
        final DOMDataBroker dataBroker = Mockito.mock(DOMDataBroker.class);
        final DOMMountPoint mountPoint = Mockito.mock(DOMMountPoint.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(this.transactionChainHandler.get()).when(dataBroker).createTransactionChain(RestConnectorProvider.transactionListener);
        final InstanceIdentifierContext<DataSchemaNode> iidContext = new InstanceIdentifierContext<>(this.iidBase, this.schemaNode, mountPoint, this.contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, this.buildBaseCont);

        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        final Response response = this.dataService.putData(null, payload, this.uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
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
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(this.iidBase, null, null, this.contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, buildList);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(this.read).read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        final MapNode data = (MapNode) payload.getData();
        final YangInstanceIdentifier.NodeIdentifierWithPredicates identifier = data.getValue().iterator().next().getIdentifier();
        final YangInstanceIdentifier node = payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(Futures.immediateCheckedFuture(false)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(this.readWrite).put(LogicalDatastoreType.CONFIGURATION, node, payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        doReturn(UriBuilder.fromUri("http://localhost:8181/restconf/15/")).when(this.uriInfo).getBaseUriBuilder();

        final Response response = this.dataService.postData(null, payload, this.uriInfo);
        assertEquals(201, response.getStatus());
    }

    @Test
    public void testDeleteData() {
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        doReturn(Futures.immediateCheckedFuture(true)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        final Response response = this.dataService.deleteData("example-jukebox:jukebox");
        assertNotNull(response);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    /**
     * Test of deleting data on mount point
     */
    @Test
    public void testDeleteDataMountPoint() {
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        doReturn(Futures.immediateCheckedFuture(true)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        final Response response = this.dataService.deleteData("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox");
        assertNotNull(response);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPatchData() throws Exception {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(this.iidBase, this.schemaNode, null, this.contextRef.get());
        final List<PATCHEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(this.iidBase)
                .node(this.containerPlayerQname)
                .node(this.leafQname)
                .build();
        entity.add(new PATCHEntity("create data", "CREATE", this.iidBase, this.buildBaseCont));
        entity.add(new PATCHEntity("replace data", "REPLACE", this.iidBase, this.buildBaseCont));
        entity.add(new PATCHEntity("delete data", "DELETE", iidleaf));
        final PATCHContext patch = new PATCHContext(iidContext, entity, "test patch id");

        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, this.buildBaseCont);
        doReturn(Futures.immediateCheckedFuture(null)).when(this.write).submit();
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        doReturn(Futures.immediateCheckedFuture(false)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(true)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);
        final PATCHStatusContext status =
                (PATCHStatusContext) this.dataService.patchData(patch, this.uriInfo).getEntity();
        assertTrue(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertEquals("replace data", status.getEditCollection().get(1).getEditId());
    }

    @Test
    public void testPatchDataMountPoint() throws Exception {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(
                this.iidBase, this.schemaNode, this.mountPoint, this.contextRef.get());
        final List<PATCHEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(this.iidBase)
                .node(this.containerPlayerQname)
                .node(this.leafQname)
                .build();
        entity.add(new PATCHEntity("create data", "CREATE", this.iidBase, this.buildBaseCont));
        entity.add(new PATCHEntity("replace data", "REPLACE", this.iidBase, this.buildBaseCont));
        entity.add(new PATCHEntity("delete data", "DELETE", iidleaf));
        final PATCHContext patch = new PATCHContext(iidContext, entity, "test patch id");

        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, this.buildBaseCont);
        doReturn(Futures.immediateCheckedFuture(null)).when(this.write).submit();
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        doReturn(Futures.immediateCheckedFuture(false)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(true)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);

        final PATCHStatusContext status =
                (PATCHStatusContext) this.dataService.patchData(patch, this.uriInfo).getEntity();
        assertTrue(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertNull(status.getGlobalErrors());
    }

    @Test
    public void testPatchDataDeleteNotExist() throws Exception {
        final Field handler = RestConnectorProvider.class.getDeclaredField("transactionChainHandler");
        final Field broker = RestConnectorProvider.class.getDeclaredField("dataBroker");

        handler.setAccessible(true);
        handler.set(RestConnectorProvider.class, mock(TransactionChainHandler.class));

        broker.setAccessible(true);
        broker.set(RestConnectorProvider.class, mock(DOMDataBroker.class));
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(this.iidBase, this.schemaNode, null, this.contextRef.get());
        final List<PATCHEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(this.iidBase)
                .node(this.containerPlayerQname)
                .node(this.leafQname)
                .build();
        entity.add(new PATCHEntity("create data", "CREATE", this.iidBase, this.buildBaseCont));
        entity.add(new PATCHEntity("remove data", "REMOVE", iidleaf));
        entity.add(new PATCHEntity("delete data", "DELETE", iidleaf));
        final PATCHContext patch = new PATCHContext(iidContext, entity, "test patch id");

        doReturn(Futures.immediateCheckedFuture(Optional.of(this.buildBaseCont))).when(this.read)
                .read(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doNothing().when(this.write).put(LogicalDatastoreType.CONFIGURATION, this.iidBase, this.buildBaseCont);
        doReturn(Futures.immediateCheckedFuture(null)).when(this.write).submit();
        doNothing().when(this.readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(Futures.immediateCheckedFuture(null)).when(this.readWrite).submit();
        doReturn(Futures.immediateCheckedFuture(false)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, this.iidBase);
        doReturn(Futures.immediateCheckedFuture(false)).when(this.readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(true).when(this.readWrite).cancel();
        final PATCHStatusContext status =
                (PATCHStatusContext) this.dataService.patchData(patch, this.uriInfo).getEntity();

        handler.set(RestConnectorProvider.class, null);
        handler.setAccessible(false);

        broker.set(RestConnectorProvider.class, null);
        broker.setAccessible(false);

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
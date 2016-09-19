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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
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
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class RestconfDataServiceImplTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";

    private ContainerNode buildBaseCont;
    private SchemaContextRef contextRef;
    private YangInstanceIdentifier iidBase;
    private DataSchemaNode schemaNode;
    private RestconfDataServiceImpl dataService;
    private QName baseQName;
    private QName containerQname;
    private QName leafQname;
    private ContainerNode buildBaseContToReplace;

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

    @Before
    public void setUp() throws Exception {
        baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        containerQname = QName.create(baseQName, "player");
        leafQname = QName.create(baseQName, "gap");
        LeafNode buildLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(leafQname))
                .withValue(0.2)
                .build();

        ContainerNode buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerQname))
                .withChild(buildLeaf)
                .build();
        buildBaseCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(baseQName))
                .withChild(buildPlayerCont)
                .build();
        buildLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(leafQname))
                .withValue(0.5)
                .build();
        buildPlayerCont = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerQname))
                .withChild(buildLeaf)
                .build();
        buildBaseContToReplace = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(baseQName))
                .withChild(buildPlayerCont)
                .build();
        iidBase = YangInstanceIdentifier.builder()
                .node(baseQName)
                .build();

        contextRef = new SchemaContextRef(TestRestconfUtils.loadSchemaContext(PATH_FOR_NEW_SCHEMA_CONTEXT));
        schemaNode = DataSchemaContextTree.from(contextRef.get()).getChild(iidBase).getDataSchemaNode();
        MockitoAnnotations.initMocks(this);
        final SchemaContextHandler schemaContextHandler = new SchemaContextHandler();

        schemaContextHandler.onGlobalContextUpdated(contextRef.get());
        dataService = new RestconfDataServiceImpl(schemaContextHandler, transactionChainHandler, mountPointServiceHandler);
        doReturn(domTransactionChain).when(transactionChainHandler).get();
        doReturn(read).when(domTransactionChain).newReadOnlyTransaction();
        doReturn(readWrite).when(domTransactionChain).newReadWriteTransaction();
        doReturn(write).when(domTransactionChain).newWriteOnlyTransaction();
    }

    @Test
    public void testReadData() {
        doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
        doReturn(Futures.immediateCheckedFuture(Optional.of(buildBaseCont))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.OPERATIONAL, iidBase);
        final Response response = dataService.readData("example-jukebox:jukebox", uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        assertEquals(buildBaseCont, ((NormalizedNodeContext) response.getEntity()).getData());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testReadDataNoData() {
        doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION,
                iidBase);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.OPERATIONAL,
                iidBase);
        final Response response = dataService.readData("example-jukebox:jukebox", uriInfo);
    }

    @Test
    public void testPutData() {
        final InstanceIdentifierContext<DataSchemaNode> iidContext = new InstanceIdentifierContext<>(iidBase, schemaNode, null, contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, buildBaseCont);

        doReturn(Futures.immediateCheckedFuture(Optional.of(buildBaseCont))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, iidBase);
        doNothing().when(write).put(LogicalDatastoreType.CONFIGURATION, iidBase, payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(write).submit();
        final Response response = dataService.putData(null, payload);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testPutDataWithMountPoint() {
        final DOMDataBroker dataBroker = Mockito.mock(DOMDataBroker.class);
        final DOMMountPoint mountPoint = Mockito.mock(DOMMountPoint.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(transactionChainHandler.get()).when(dataBroker).createTransactionChain(RestConnectorProvider.transactionListener);
        final InstanceIdentifierContext<DataSchemaNode> iidContext = new InstanceIdentifierContext<>(iidBase, schemaNode, mountPoint, contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, buildBaseCont);

        doReturn(Futures.immediateCheckedFuture(Optional.of(buildBaseCont))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, iidBase);
        doNothing().when(write).put(LogicalDatastoreType.CONFIGURATION, iidBase, payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(write).submit();
        final Response response = dataService.putData(null, payload);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testPostData() {
        final QName listQname = QName.create(baseQName, "playlist");
        final QName listKeyQname = QName.create(baseQName, "name");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listQname, listKeyQname, "name of band");
        final LeafNode<Object> content = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of band")
                .build();
        final LeafNode<Object> content2 = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "description")))
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

        doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iidBase, null, null, contextRef.get());
        final NormalizedNodeContext payload = new NormalizedNodeContext(iidContext, buildList);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read).read(LogicalDatastoreType.CONFIGURATION, iidBase);
        final MapNode data = (MapNode) payload.getData();
        final YangInstanceIdentifier.NodeIdentifierWithPredicates identifier = data.getValue().iterator().next().getIdentifier();
        final YangInstanceIdentifier node = payload.getInstanceIdentifierContext().getInstanceIdentifier().node(identifier);
        doReturn(Futures.immediateCheckedFuture(false)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, payload.getData());
        doReturn(Futures.immediateCheckedFuture(null)).when(readWrite).submit();
        doReturn(UriBuilder.fromUri("http://localhost:8181/restconf/15/")).when(uriInfo).getBaseUriBuilder();

        final Response response = dataService.postData(null, payload, uriInfo);
        assertEquals(201, response.getStatus());
    }

    @Test
    public void testDeleteData() {
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(Futures.immediateCheckedFuture(null)).when(readWrite).submit();
        doReturn(Futures.immediateCheckedFuture(true)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        final Response response = dataService.deleteData("example-jukebox:jukebox");
        assertNotNull(response);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPatchData() throws Exception {
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iidBase, schemaNode, null, contextRef.get());
        final List<PATCHEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(iidBase)
                .node(containerQname)
                .node(leafQname)
                .build();
        entity.add(new PATCHEntity("create data", "CREATE", iidBase, buildBaseCont));
        entity.add(new PATCHEntity("replace data", "REPLACE", iidBase, buildBaseContToReplace));
        entity.add(new PATCHEntity("delete data", "DELETE", iidleaf));
        final PATCHContext patch = new PATCHContext(iidContext, entity, "test patch id");

        doReturn(Futures.immediateCheckedFuture(Optional.of(buildBaseCont))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, iidBase);
        doNothing().when(write).put(LogicalDatastoreType.CONFIGURATION, iidBase, buildBaseCont);
        doReturn(Futures.immediateCheckedFuture(null)).when(write).submit();
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(Futures.immediateCheckedFuture(null)).when(readWrite).submit();
        doReturn(Futures.immediateCheckedFuture(false)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(Futures.immediateCheckedFuture(true)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);
        final PATCHStatusContext status = dataService.patchData(patch, uriInfo);
        assertTrue(status.isOk());
        assertEquals(3, status.getEditCollection().size());
        assertEquals("replace data", status.getEditCollection().get(1).getEditId());
    }

    @Test
    public void testPatchDataDeleteNotExist() throws Exception {
        final Field handler = RestConnectorProvider.class.getDeclaredField("transactionChainHandler");
        final Field broker = RestConnectorProvider.class.getDeclaredField("dataBroker");

        handler.setAccessible(true);
        handler.set(RestConnectorProvider.class, mock(TransactionChainHandler.class));

        broker.setAccessible(true);
        broker.set(RestConnectorProvider.class, mock(DOMDataBroker.class));
        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iidBase, schemaNode, null, contextRef.get());
        final List<PATCHEntity> entity = new ArrayList<>();
        final YangInstanceIdentifier iidleaf = YangInstanceIdentifier.builder(iidBase)
                .node(containerQname)
                .node(leafQname)
                .build();
        entity.add(new PATCHEntity("create data", "CREATE", iidBase, buildBaseCont));
        entity.add(new PATCHEntity("remove data", "REMOVE", iidleaf));
        entity.add(new PATCHEntity("delete data", "DELETE", iidleaf));
        final PATCHContext patch = new PATCHContext(iidContext, entity, "test patch id");

        doReturn(Futures.immediateCheckedFuture(Optional.of(buildBaseCont))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, iidBase);
        doNothing().when(write).put(LogicalDatastoreType.CONFIGURATION, iidBase, buildBaseCont);
        doReturn(Futures.immediateCheckedFuture(null)).when(write).submit();
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(Futures.immediateCheckedFuture(null)).when(readWrite).submit();
        doReturn(Futures.immediateCheckedFuture(false)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidBase);
        doReturn(Futures.immediateCheckedFuture(false)).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, iidleaf);
        doReturn(true).when(readWrite).cancel();
        final PATCHStatusContext status = dataService.patchData(patch, uriInfo);

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
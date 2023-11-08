/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfDataServiceImplTest extends AbstractJukeboxTest {
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
    private UriInfo uriInfo;
    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMDataTreeReadTransaction read;
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
    private DOMRpcService rpcService;
    @Mock
    private MultivaluedMap<String, String> queryParamenters;
    @Mock
    private AsyncResponse asyncResponse;
    @Captor
    private ArgumentCaptor<Response> responseCaptor;

    private RestconfDataServiceImpl dataService;

    @Before
    public void setUp() throws Exception {
        doReturn(Set.of()).when(queryParamenters).entrySet();
        doReturn(queryParamenters).when(uriInfo).getQueryParameters();

        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        final var dataBroker = mock(DOMDataBroker.class);
        doReturn(read).when(dataBroker).newReadOnlyTransaction();
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();

        final DatabindProvider databindProvider = () -> DatabindContext.ofModel(JUKEBOX_SCHEMA);
        dataService = new RestconfDataServiceImpl(databindProvider,
            new MdsalRestconfServer(databindProvider, dataBroker, rpcService, mountPointService), actionService);
        doReturn(Optional.of(mountPoint)).when(mountPointService)
                .getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(FixedDOMSchemaService.of(JUKEBOX_SCHEMA))).when(mountPoint)
                .getService(DOMSchemaService.class);
        doReturn(Optional.of(mountDataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(read).when(mountDataBroker).newReadOnlyTransaction();
        doReturn(readWrite).when(mountDataBroker).newReadWriteTransaction();
    }

    @Test
    public void testReadData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(EMPTY_JUKEBOX))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(read).read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);
        final Response response = dataService.readData("example-jukebox:jukebox", uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        assertEquals(EMPTY_JUKEBOX, ((NormalizedNodePayload) response.getEntity()).data());
    }

    @Test
    public void testReadRootData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(wrapNodeByDataRootContainer(CONFIG_JUKEBOX))))
                .when(read)
                .read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
        doReturn(immediateFluentFuture(Optional.of(wrapNodeByDataRootContainer(OPER_JUKEBOX))))
                .when(read)
                .read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of());
        final Response response = dataService.readData(uriInfo);
        assertNotNull(response);
        assertEquals(200, response.getStatus());

        final NormalizedNode data = ((NormalizedNodePayload) response.getEntity()).data();
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
        doReturn(immediateFluentFuture(Optional.of(CONFIG_JUKEBOX))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFluentFuture(Optional.of(OPER_JUKEBOX))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);

        final Response response = dataService.readData(
                "example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox", uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain all child nodes from config and operational containers merged in one container
        final NormalizedNode data = ((NormalizedNodePayload) response.getEntity()).data();
        assertTrue(data instanceof ContainerNode);
        assertEquals(3, ((ContainerNode) data).size());
        assertNotNull(((ContainerNode) data).childByArg(CONT_PLAYER.name()));
        assertNotNull(((ContainerNode) data).childByArg(LIBRARY_NID));
        assertNotNull(((ContainerNode) data).childByArg(PLAYLIST_NID));
    }

    @Test
    public void testReadDataNoData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(read).read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFluentFuture(Optional.empty()))
                .when(read).read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);

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
        doReturn(immediateFluentFuture(Optional.of(CONFIG_JUKEBOX))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);

        final Response response = dataService.readData("example-jukebox:jukebox", uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain only config data
        final NormalizedNode data = ((NormalizedNodePayload) response.getEntity()).data();

        // config data present
        assertNotNull(((ContainerNode) data).childByArg(CONT_PLAYER.name()));
        assertNotNull(((ContainerNode) data).childByArg(LIBRARY_NID));

        // state data absent
        assertNull(((ContainerNode) data).childByArg(PLAYLIST_NID));
    }

    /**
     * Read data from operational datastore according to content parameter.
     */
    @Test
    public void testReadDataOperationalTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put("content", List.of("nonconfig"));

        doReturn(parameters).when(uriInfo).getQueryParameters();
        doReturn(immediateFluentFuture(Optional.of(OPER_JUKEBOX))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, JUKEBOX_IID);

        final Response response = dataService.readData("example-jukebox:jukebox", uriInfo);

        assertNotNull(response);
        assertEquals(200, response.getStatus());

        // response must contain only operational data
        final NormalizedNode data = ((NormalizedNodePayload) response.getEntity()).data();

        // state data present
        assertNotNull(((ContainerNode) data).childByArg(CONT_PLAYER.name()));
        assertNotNull(((ContainerNode) data).childByArg(PLAYLIST_NID));

        // config data absent
        assertNull(((ContainerNode) data).childByArg(LIBRARY_NID));
    }

    @Test
    public void testPutData() {
        doReturn(immediateTrueFluentFuture()).when(read)
                .exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);

        doReturn(true).when(asyncResponse).resume(responseCaptor.capture());
        dataService.putDataJSON("example-jukebox:jukebox", uriInfo, stringInputStream("""
            {
              "example-jukebox:jukebox" : {
                 "player": {
                   "gap": "0.2"
                 }
              }
            }"""), asyncResponse);
        final var response = responseCaptor.getValue();
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPutDataWithMountPoint() {
        doReturn(immediateTrueFluentFuture()).when(read)
                .exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID, EMPTY_JUKEBOX);

        doReturn(true).when(asyncResponse).resume(responseCaptor.capture());
        dataService.putDataXML("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox",
            uriInfo, stringInputStream("""
                <jukebox xmlns="http://example.com/ns/example-jukebox">
                  <player>
                    <gap>0.2</gap>
                  </player>
                </jukebox>"""), asyncResponse);
        final var response = responseCaptor.getValue();
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    private static InputStream stringInputStream(final String str) {
        return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testPostData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID,
            Builders.containerBuilder().withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME)).build());
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        final var captor = ArgumentCaptor.forClass(Response.class);
        doReturn(true).when(asyncResponse).resume(captor.capture());
        dataService.postDataJSON(stringInputStream("""
            {
              "example-jukebox:jukebox" : {
              }
            }"""), uriInfo, asyncResponse);
        final var response = captor.getValue();
        assertEquals(201, response.getStatus());
        assertEquals(URI.create("http://localhost:8181/rests/data/example-jukebox:jukebox"), response.getLocation());
    }

    @Test
    public void testPostMapEntryData() {
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        final var node = PLAYLIST_IID.node(BAND_ENTRY.name());
        doReturn(immediateFalseFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, node);
        doNothing().when(readWrite).put(LogicalDatastoreType.CONFIGURATION, node, BAND_ENTRY);
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        final var captor = ArgumentCaptor.forClass(Response.class);
        doReturn(true).when(asyncResponse).resume(captor.capture());
        dataService.postDataJSON("example-jukebox:jukebox", stringInputStream("""
            {
              "example-jukebox:playlist" : {
                "name" : "name of band",
                "description" : "band description"
              }
            }"""), uriInfo, asyncResponse);
        final var response = captor.getValue();
        assertEquals(201, response.getStatus());
        assertEquals(URI.create("http://localhost:8181/rests/data/example-jukebox:jukebox/playlist=name%20of%20band"),
            response.getLocation());
    }

    @Test
    public void testDeleteData() {
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        final var captor = ArgumentCaptor.forClass(Response.class);
        doReturn(true).when(asyncResponse).resume(captor.capture());
        dataService.deleteData("example-jukebox:jukebox", asyncResponse);

        assertEquals(204, captor.getValue().getStatus());
    }

    @Test
    public void testDeleteDataNotExisting() {
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
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
        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        final var captor = ArgumentCaptor.forClass(Response.class);
        doReturn(true).when(asyncResponse).resume(captor.capture());
        dataService.deleteData("example-jukebox:jukebox/yang-ext:mount/example-jukebox:jukebox", asyncResponse);

        assertEquals(204, captor.getValue().getStatus());
    }

    @Test
    public void testPatchData() {
        final var patch = new PatchContext("test patch id", List.of(
            new PatchEntity("create data", Operation.Create, JUKEBOX_IID, EMPTY_JUKEBOX),
            new PatchEntity("replace data", Operation.Replace, JUKEBOX_IID, EMPTY_JUKEBOX),
            new PatchEntity("delete data", Operation.Delete, GAP_IID)));

        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(true).when(asyncResponse).resume(responseCaptor.capture());
        dataService.yangPatchData(JUKEBOX_SCHEMA, patch, null, asyncResponse);
        final var response = responseCaptor.getValue();
        assertEquals(200, response.getStatus());
        final var status = assertInstanceOf(PatchStatusContext.class, response.getEntity());

        assertTrue(status.ok());
        assertEquals(3, status.editCollection().size());
        assertEquals("replace data", status.editCollection().get(1).getEditId());
    }

    @Test
    public void testPatchDataMountPoint() throws Exception {
        final var patch = new PatchContext("test patch id", List.of(
            new PatchEntity("create data", Operation.Create, JUKEBOX_IID, EMPTY_JUKEBOX),
            new PatchEntity("replace data", Operation.Replace, JUKEBOX_IID, EMPTY_JUKEBOX),
            new PatchEntity("delete data", Operation.Delete, GAP_IID)));

        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture()).when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);

        doReturn(true).when(asyncResponse).resume(responseCaptor.capture());
        dataService.yangPatchData(JUKEBOX_SCHEMA, patch, mountPoint, asyncResponse);
        final var response = responseCaptor.getValue();
        assertEquals(200, response.getStatus());
        final var status = assertInstanceOf(PatchStatusContext.class, response.getEntity());

        assertTrue(status.ok());
        assertEquals(3, status.editCollection().size());
        assertNull(status.globalErrors());
    }

    @Test
    public void testPatchDataDeleteNotExist() {
        final var patch = new PatchContext("test patch id", List.of(
            new PatchEntity("create data", Operation.Create, JUKEBOX_IID, EMPTY_JUKEBOX),
            new PatchEntity("remove data", Operation.Remove, GAP_IID),
            new PatchEntity("delete data", Operation.Delete, GAP_IID)));

        doNothing().when(readWrite).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFalseFluentFuture())
                .when(readWrite).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(true).when(readWrite).cancel();

        doReturn(true).when(asyncResponse).resume(responseCaptor.capture());
        dataService.yangPatchData(JUKEBOX_SCHEMA, patch, null, asyncResponse);
        final var response = responseCaptor.getValue();
        assertEquals(409, response.getStatus());
        final var status = assertInstanceOf(PatchStatusContext.class, response.getEntity());

        assertFalse(status.ok());
        assertEquals(3, status.editCollection().size());
        assertTrue(status.editCollection().get(0).isOk());
        assertTrue(status.editCollection().get(1).isOk());
        assertFalse(status.editCollection().get(2).isOk());
        assertFalse(status.editCollection().get(2).getEditErrors().isEmpty());
        final String errorMessage = status.editCollection().get(2).getEditErrors().get(0).getErrorMessage();
        assertEquals("Data does not exist", errorMessage);
    }
}

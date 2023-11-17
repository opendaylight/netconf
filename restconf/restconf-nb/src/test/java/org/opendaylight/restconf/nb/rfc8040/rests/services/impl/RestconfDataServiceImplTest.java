/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;

import java.net.URI;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MultivaluedHashMap;
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
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfDataServiceImplTest extends AbstractJukeboxTest {
    @Mock
    private UriInfo uriInfo;
    @Mock
    private DOMDataTreeReadWriteTransaction readWrite;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMDataBroker mountDataBroker;
    @Mock
    private DOMActionService actionService;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private AsyncResponse asyncResponse;
    @Captor
    private ArgumentCaptor<Response> responseCaptor;

    private RestconfDataServiceImpl dataService;

    @Before
    public void setUp() throws Exception {
        doReturn(CommitInfo.emptyFluentFuture()).when(readWrite).commit();

        final var dataBroker = mock(DOMDataBroker.class);
        doReturn(readWrite).when(dataBroker).newReadWriteTransaction();

        final DatabindProvider databindProvider = () -> DatabindContext.ofModel(JUKEBOX_SCHEMA);
        dataService = new RestconfDataServiceImpl(databindProvider,
            new MdsalRestconfServer(databindProvider, dataBroker, rpcService, actionService, mountPointService));
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

}

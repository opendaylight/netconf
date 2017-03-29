/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.restconfsb.communicator.api.http.ConnectionListener;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfDeviceManagerTest {

    private static final File CACHE = new File("cache/schema/");
    private static final String NODE_1 = "node1";

    @Mock
    private SenderFactory senderFactory;
    @Mock
    private ThreadPool processingeExecutor;
    @Mock
    private ScheduledThreadPool reconnectExecutor;
    @Mock
    private DOMMountPointService mountpointService;
    @Mock
    private ConnectionListener listener;
    @Mock
    private Sender sender;
    @Mock
    private DOMMountPointService.DOMMountPointBuilder mountPointBuilder;
    @Mock
    private ObjectRegistration registration;
    private Node node;
    private RestconfDeviceManager manager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final RestconfNode restconfNode = new RestconfNodeBuilder().build();
        node = new NodeBuilder()
                .setNodeId(new NodeId(NODE_1))
                .addAugmentation(RestconfNode.class, restconfNode)
                .build();
        doReturn(Executors.newSingleThreadExecutor()).when(processingeExecutor).getExecutor();
        doReturn(Executors.newSingleThreadScheduledExecutor()).when(reconnectExecutor).getExecutor();
        doReturn(sender).when(senderFactory).createSender(eq(node), any(ScheduledExecutorService.class));
        doReturn(null).when(sender).registerConnectionListener(listener);
        doReturn(null).when(sender).registerSseListener(any(SseListener.class));
        final InputStream inputStream = getClass().getResourceAsStream("/xml/modules.xml");
        doReturn(Futures.immediateFuture(inputStream)).when(sender).get(Request.createRequestWithoutBody("/data/ietf-yang-library:modules", Request.RestconfMediaType.XML_DATA));
        doReturn(Futures.immediateFailedFuture(new NotFoundException("not found"))).when(sender).get(Request.createRequestWithoutBody("/data/ietf-restconf-monitoring:restconf-state/streams", Request.RestconfMediaType.XML_DATA));
        final URL inputUrl1 = getClass().getResource("/cache/schema/all/ietf-yang-types@2010-09-24.yang");
        final URL inputUrl2 = getClass().getResource("/cache/schema/all/ietf-restconf@2013-10-19.yang");
        CACHE.mkdirs();
        FileUtils.copyURLToFile(inputUrl1, new File(CACHE, "ietf-yang-types@2010-09-24.yang"));
        FileUtils.copyURLToFile(inputUrl2, new File(CACHE, "ietf-restconf@2013-10-19.yang"));
        doReturn(mountPointBuilder).when(mountpointService).createMountPoint(any(YangInstanceIdentifier.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addInitialSchemaContext(any(SchemaContext.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMDataBroker.class), any(DOMDataBroker.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMRpcService.class), any(DOMRpcService.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMNotificationService.class), any(DOMNotificationService.class));
        doReturn(registration).when(mountPointBuilder).register();
        doNothing().when(registration).close();
        doNothing().when(sender).close();
        manager = new RestconfDeviceManager(node, senderFactory, processingeExecutor, reconnectExecutor, mountpointService);
    }

    @Test
    public void testConnectAndDisconnect() throws Exception {
        final List<Module> modules = manager.connect(listener).get();
        Assert.assertEquals(2, modules.size());
        verify(mountpointService).createMountPoint(new RestconfDeviceId(NODE_1).getTopologyPath());
        manager.disconnect();
        verify(registration).close();
        verify(sender).close();
    }

    @Test
    public void testClose() throws Exception {
        final List<Module> modules = manager.connect(listener).get();
        Assert.assertEquals(2, modules.size());
        verify(mountpointService).createMountPoint(new RestconfDeviceId(NODE_1).getTopologyPath());
        manager.close();
        verify(registration).close();
        verify(sender).close();
    }

    @After
    public void tearDown() throws Exception {
        final File cache = new File("cache");
        FileUtils.deleteDirectory(cache);
    }
}
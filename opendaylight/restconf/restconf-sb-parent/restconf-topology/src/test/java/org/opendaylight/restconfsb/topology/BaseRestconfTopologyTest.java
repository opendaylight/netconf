/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.restconfsb.communicator.api.http.ConnectionListener;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.restconfsb.communicator.impl.sender.NodeConnectionException;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class BaseRestconfTopologyTest {

    private static final File CACHE = new File("cache/schema/");
    private static final RestconfNode NODE = new RestconfNodeBuilder()
            .setAddress(new Host(new DomainName("localhost")))
            .setPort(new PortNumber(9999))
            .setRequestTimeout(5000)
            .setHttps(false)
            .build();

    @Mock
    private SenderFactory senderFactory;
    @Mock
    private ThreadPool processingExecutor;
    @Mock
    private ScheduledThreadPool reconnectExecutor;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private ListenerRegistration<BaseRestconfTopology> listenerRegistration;
    @Mock
    private BindingTransactionChain bindingTransactionChain;
    @Mock
    private WriteTransaction writeTransaction;
    @Mock
    private CheckedFuture future;
    @Mock
    private DataTreeModification treeModification;
    @Mock
    private DataObjectModification objectModification;
    @Mock
    private PathArgument argument;
    @Mock
    private Sender sender;
    @Mock
    private DOMMountPointService.DOMMountPointBuilder mountPointBuilder;
    @Mock
    private ObjectRegistration registration;

    private ScheduledExecutorService scheduledService;
    private ExecutorService service;
    private BaseRestconfTopology restconfTopology;
    private ClusteredStatus nodeStatus;
    private ClusteredNode clusteredNode;
    private Node restconfNode;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException, NodeConnectionException, IOException {
        initMocks(this);

        final URL inputUrl1 = getClass().getResource("/yang/schema/all/ietf-yang-types@2010-09-24.yang");
        final URL inputUrl2 = getClass().getResource("/yang/schema/all/ietf-restconf@2013-10-19.yang");
        CACHE.mkdirs();
        FileUtils.copyURLToFile(inputUrl1, new File(CACHE, "ietf-yang-types@2010-09-24.yang"));
        FileUtils.copyURLToFile(inputUrl2, new File(CACHE, "ietf-restconf@2013-10-19.yang"));

        doReturn(listenerRegistration).when(dataBroker).registerDataTreeChangeListener(any(DataTreeIdentifier.class), any(BaseRestconfTopology.class));
        doReturn(bindingTransactionChain).when(dataBroker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(writeTransaction).when(bindingTransactionChain).newWriteOnlyTransaction();
        doReturn(future).when(writeTransaction).submit();
        doNothing().when(future).addListener(any(Runnable.class), any(Executor.class));
        scheduledService = Executors.newSingleThreadScheduledExecutor();
        service = Executors.newSingleThreadExecutor();
        doReturn(service).when(processingExecutor).getExecutor();
        doReturn(scheduledService).when(reconnectExecutor).getExecutor();

        nodeStatus = new ClusteredStatusBuilder()
                .setNode("10.10.10.10")
                .setStatus(ClusteredStatus.Status.Connected)
                .build();
        clusteredNode = new ClusteredNodeBuilder()
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder().setClusteredStatus(Collections.singletonList(nodeStatus)).build())
                .build();
        restconfNode = new NodeBuilder()
                .setNodeId(new NodeId("Test node"))
                .addAugmentation(RestconfNode.class, NODE)
                .addAugmentation(ClusteredNode.class, clusteredNode)
                .build();
        doReturn(sender).when(senderFactory).createSender(restconfNode, scheduledService);
        doNothing().when(writeTransaction).merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        doNothing().when(writeTransaction).put(any(LogicalDatastoreType.class),
                eq(new RestconfDeviceId(restconfNode.getNodeId().getValue()).getBindingTopologyPath()),
                any(Node.class));
        doReturn(null).when(sender).registerConnectionListener(any(ConnectionListener.class));
        final Request requestWithoutBody =
                Request.createRequestWithoutBody("/data/ietf-restconf-monitoring:restconf-state/streams", Request.RestconfMediaType.XML_DATA);
        final Request requestWithoutBody1 = Request.createRequestWithoutBody("/data/ietf-yang-library:modules", Request.RestconfMediaType.XML_DATA);
        doReturn(Futures.immediateFailedFuture(new NotFoundException("not found"))).when(sender).get(requestWithoutBody);
        final ListenableFuture<InputStream> inputStream = inputStreamModules();
        doReturn(inputStream).when(sender).get(requestWithoutBody1);
        doReturn(null).when(sender).registerSseListener(any(SseListener.class));
        doReturn(mountPointBuilder).when(mountPointService).createMountPoint(any(YangInstanceIdentifier.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addInitialSchemaContext(any(SchemaContext.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMDataBroker.class), any(DOMDataBroker.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMRpcService.class), any(DOMRpcService.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMNotificationService.class), any(DOMNotificationService.class));
        doReturn(registration).when(mountPointBuilder).register();
        doNothing().when(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));

        restconfTopology = new BaseRestconfTopology(senderFactory, processingExecutor, reconnectExecutor,
                dataBroker, mountPointService);
    }

    @Test
    public void testGetTopologyId() throws Exception {
        assertEquals("topology-restconf", restconfTopology.getTopologyId());
    }

    @Test
    public void testConnectNode() throws Exception {
        final ListenableFuture<List<Module>> futureLocal =
                restconfTopology.connectNode(NodeId.getDefaultInstance("test-value"), restconfNode);
        final List<Module> modules = futureLocal.get();
        assertEquals(modules.size(), 2);
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf", modules.get(0).getNamespace().getValue());
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-yang-types", modules.get(1).getNamespace().getValue());
    }

    private ListenableFuture<InputStream> inputStreamModules() {
        final SettableFuture<InputStream> is = SettableFuture.create();
        is.set(this.getClass().getResourceAsStream("/xml/modules.xml"));
        return is;
    }

    @After
    public void tearDown() throws Exception {
        final File cache = new File("cache");
        FileUtils.deleteDirectory(cache);
    }
}
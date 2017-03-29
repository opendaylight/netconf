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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfNodeManagerTest {

    private static final RestconfNode NODE = new RestconfNodeBuilder()
            .setAddress(new Host(new DomainName("localhost")))
            .setPort(new PortNumber(9999))
            .setRequestTimeout(5000)
            .setHttps(false)
            .build();
    private static final File CACHE = new File("cache/schema/");
    private RestconfNodeManager restconfNodeManager;

    private Node configNode;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private SenderFactory senderFactory;
    @Mock
    private ThreadPool processingExecutor;
    @Mock
    private ScheduledThreadPool reconnectExecutor;
    @Mock
    private BindingTransactionChain txChain;
    @Mock
    private WriteTransaction writeTransaction;
    @Mock
    private DOMMountPointService mountpointService;
    @Mock
    private Sender sender;
    @Mock
    private DOMMountPointService.DOMMountPointBuilder mountPointBuilder;
    @Mock
    private ObjectRegistration registration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final URL inputUrl1 = getClass().getResource("/yang/schema/all/ietf-yang-types@2010-09-24.yang");
        final URL inputUrl2 = getClass().getResource("/yang/schema/all/ietf-restconf@2013-10-19.yang");
        CACHE.mkdirs();
        FileUtils.copyURLToFile(inputUrl1, new File(CACHE, "ietf-yang-types@2010-09-24.yang"));
        FileUtils.copyURLToFile(inputUrl2, new File(CACHE, "ietf-restconf@2013-10-19.yang"));
        doReturn(Executors.newSingleThreadScheduledExecutor()).when(reconnectExecutor).getExecutor();
        doReturn(Executors.newSingleThreadExecutor()).when(processingExecutor).getExecutor();
        doReturn(txChain).when(dataBroker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(writeTransaction).when(txChain).newWriteOnlyTransaction();
        doReturn(null).when(sender).registerSseListener(any(SseListener.class));
        doReturn(null).when(sender).registerConnectionListener(any(ConnectionListener.class));
        final Request requestWithoutBody = Request.createRequestWithoutBody("/data/ietf-yang-library:modules", Request.RestconfMediaType.XML_DATA);
        final Request requestWithoutBody1 = Request.createRequestWithoutBody("/data/ietf-restconf-monitoring:restconf-state/streams", Request.RestconfMediaType.XML_DATA);
        doReturn(Futures.immediateFailedFuture(new NotFoundException("not found"))).when(sender).get(requestWithoutBody1);
        final ListenableFuture<InputStream> inputStream = inputStreamModules();
        doReturn(inputStream).when(sender).get(requestWithoutBody);
        final ScheduledExecutorService executor = reconnectExecutor.getExecutor();
        final ClusteredStatus nodeStatus = new ClusteredStatusBuilder()
                .setNode("10.10.10.10")
                .setStatus(ClusteredStatus.Status.Connected)
                .build();
        final ClusteredNode clusteredNode = new ClusteredNodeBuilder()
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder().setClusteredStatus(Collections.singletonList(nodeStatus)).build())
                .build();
        configNode = new NodeBuilder()
                .setNodeId(new NodeId("Test node"))
                .addAugmentation(RestconfNode.class, NODE)
                .addAugmentation(ClusteredNode.class, clusteredNode)
                .build();
        doReturn(sender).when(senderFactory).createSender(eq(configNode), eq(executor));

        doReturn(mountPointBuilder).when(mountpointService).createMountPoint(any(YangInstanceIdentifier.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addInitialSchemaContext(any(SchemaContext.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMDataBroker.class), any(DOMDataBroker.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMRpcService.class), any(DOMRpcService.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMNotificationService.class), any(DOMNotificationService.class));
        doReturn(registration).when(mountPointBuilder).register();
        final RestconfDeviceId deviceId = new RestconfDeviceId(configNode.getNodeId().getValue());
        doNothing().when(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL), eq(deviceId.getBindingTopologyPath()), any(Node.class));
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTransaction).submit();
    }

    @Test
    public void testConnect() throws Exception {
        restconfNodeManager = new RestconfNodeManager(configNode, mountpointService, dataBroker, senderFactory,
                processingExecutor, reconnectExecutor);
        final List<Module> moduleList = restconfNodeManager.connect().get();
        assertEquals(moduleList.size(), 2);
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf", moduleList.get(0).getNamespace().getValue());
        verify(senderFactory).createSender(configNode, reconnectExecutor.getExecutor());
    }

    @Test(expected = ExecutionException.class)
    public void testConnectFail() throws Exception {
        restconfNodeManager = new RestconfNodeManager(configNode, mountpointService, dataBroker, null,
                processingExecutor, reconnectExecutor);
        try {
            restconfNodeManager.connect().get();
        } catch (final Exception e) {
            final List<Module> modules = new ArrayList<>();
            final RestconfDeviceId deviceId = new RestconfDeviceId(configNode.getNodeId().getValue());
            final RestconfNode restconfNodeStatus = new RestconfNodeBuilder()
                    .setStatus(NodeStatus.Status.Failed)
                    .setModule(modules)
                    .build();
            final Node nodeStatus = new NodeBuilder()
                    .setNodeId(configNode.getNodeId())
                    .setKey(configNode.getKey())
                    .addAugmentation(RestconfNode.class, restconfNodeStatus)
                    .build();
            verify(writeTransaction, timeout(2000)).put(LogicalDatastoreType.OPERATIONAL, deviceId.getBindingTopologyPath(), nodeStatus);
            throw e;
        }

    }

    @Test
    public void deleteConnection() throws Exception {
        restconfNodeManager = new RestconfNodeManager(configNode, mountpointService, dataBroker, senderFactory,
                processingExecutor, reconnectExecutor);
        final RestconfDeviceId deviceId = new RestconfDeviceId(configNode.getNodeId().getValue());
        doNothing().when(writeTransaction).delete(LogicalDatastoreType.OPERATIONAL, deviceId.getBindingTopologyPath());
        restconfNodeManager.connect().get();
        restconfNodeManager.disconnect().get();
        final RestconfDeviceId id = new RestconfDeviceId(configNode.getNodeId().getValue());
        verify(writeTransaction).delete(LogicalDatastoreType.OPERATIONAL, id.getBindingTopologyPath());
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

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationPublishService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.messagebus.spi.EventSource;
import org.opendaylight.controller.messagebus.spi.EventSourceRegistry;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class NetconfEventSourceManagerTest {

    NetconfEventSourceManager netconfEventSourceManager;
    ListenerRegistration listenerRegistrationMock;
    DOMMountPointService domMountPointServiceMock;
    MountPointService mountPointServiceMock;
    EventSourceRegistry eventSourceTopologyMock;
    AsyncDataChangeEvent asyncDataChangeEventMock;
    RpcProviderRegistry rpcProviderRegistryMock;
    EventSourceRegistry eventSourceRegistry;

    @BeforeClass
    public static void initTestClass() throws IllegalAccessException, InstantiationException {
    }

    @Before
    public void setUp() throws Exception {
        DataBroker dataBrokerMock = mock(DataBroker.class);
        DOMNotificationPublishService domNotificationPublishServiceMock = mock(DOMNotificationPublishService.class);
        domMountPointServiceMock = mock(DOMMountPointService.class);
        eventSourceTopologyMock = mock(EventSourceRegistry.class);
        rpcProviderRegistryMock = mock(RpcProviderRegistry.class);
        eventSourceRegistry = mock(EventSourceRegistry.class);

        listenerRegistrationMock = mock(ListenerRegistration.class);
        doReturn(listenerRegistrationMock).when(dataBrokerMock).registerDataChangeListener(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class), any(NetconfEventSourceManager.class), eq(
            AsyncDataBroker.DataChangeScope.SUBTREE));

        DOMMountPoint domMountPointMock = mock(DOMMountPoint.class);
        Optional<DOMMountPoint> optionalDomMountServiceMock = Optional.of(domMountPointMock);
        doReturn(optionalDomMountServiceMock).when(domMountPointServiceMock).getMountPoint((YangInstanceIdentifier)notNull());
        DOMDataBroker mpDataBroker = mock(DOMDataBroker.class);
        doReturn(Optional.of(mpDataBroker)).when(domMountPointMock).getService(DOMDataBroker.class);
        doReturn(Optional.of(mock(DOMRpcService.class))).when(domMountPointMock).getService(DOMRpcService.class);
        doReturn(Optional.of(mock(DOMNotificationService.class))).when(domMountPointMock).getService(DOMNotificationService.class);

        DOMDataReadOnlyTransaction rtx = mock(DOMDataReadOnlyTransaction.class);
        doReturn(rtx).when(mpDataBroker).newReadOnlyTransaction();
        CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> checkFeature = Futures.immediateCheckedFuture(Optional.of(NetconfTestUtils.getStreamsNode("stream-1")));

        YangInstanceIdentifier pathStream = YangInstanceIdentifier.builder().node(Netconf.QNAME).node(Streams.QNAME).build();
        doReturn(checkFeature).when(rtx).read(LogicalDatastoreType.OPERATIONAL, pathStream);

        netconfEventSourceManager = new NetconfEventSourceManager(dataBrokerMock,
                domNotificationPublishServiceMock, domMountPointServiceMock, eventSourceRegistry);
        netconfEventSourceManager.streamMap = new HashMap();
    }

    @Test
    public void onDataChangedCreateEventSourceTestByCreateEntry() throws Exception {
        onDataChangedTestHelper(true,false,true, NetconfTestUtils.notification_capability_prefix);
        netconfEventSourceManager.onDataChanged(asyncDataChangeEventMock);
        verify(eventSourceRegistry, times(1)).registerEventSource(any(EventSource.class));
    }

    @Test
    public void onDataChangedCreateEventSourceTestByUpdateEntry() throws Exception {
        onDataChangedTestHelper(false,true,true, NetconfTestUtils.notification_capability_prefix);
        netconfEventSourceManager.onDataChanged(asyncDataChangeEventMock);
        verify(eventSourceRegistry, times(1)).registerEventSource(any(EventSource.class));
    }

    @Test
    public void onDataChangedCreateEventSourceTestNotNeconf() throws Exception {
        onDataChangedTestHelper(false,true,false, NetconfTestUtils.notification_capability_prefix);
        netconfEventSourceManager.onDataChanged(asyncDataChangeEventMock);
        verify(eventSourceRegistry, times(0)).registerEventSource(any(EventSource.class));
    }

    @Test
    public void onDataChangedCreateEventSourceTestNotNotificationCapability() throws Exception {
        onDataChangedTestHelper(true,false,true,"bad-prefix");
        netconfEventSourceManager.onDataChanged(asyncDataChangeEventMock);
        verify(eventSourceRegistry, times(0)).registerEventSource(any(EventSource.class));
    }

    private void onDataChangedTestHelper(boolean create, boolean update, boolean isNetconf, String notificationCapabilityPrefix) throws Exception{
        asyncDataChangeEventMock = mock(AsyncDataChangeEvent.class);
        Map<InstanceIdentifier, DataObject> mapCreate = new HashMap<>();
        Map<InstanceIdentifier, DataObject> mapUpdate = new HashMap<>();

        Node node01;
        String nodeId = "Node01";
        doReturn(mapCreate).when(asyncDataChangeEventMock).getCreatedData();
        doReturn(mapUpdate).when(asyncDataChangeEventMock).getUpdatedData();

        if(isNetconf){
            node01 = NetconfTestUtils
                .getNetconfNode(nodeId, "node01.test.local", ConnectionStatus.Connected, notificationCapabilityPrefix);

        } else {
            node01 = NetconfTestUtils.getNode(nodeId);
        }

        if(create){
            mapCreate.put(NetconfTestUtils.getInstanceIdentifier(node01), node01);
        }
        if(update){
            mapUpdate.put(NetconfTestUtils.getInstanceIdentifier(node01), node01);
        }

    }

}
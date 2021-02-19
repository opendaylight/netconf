/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.messagebus.spi.EventSource;
import org.opendaylight.controller.messagebus.spi.EventSourceRegistry;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

@Deprecated(forRemoval = true)
public class NetconfEventSourceManagerTest extends AbstractCodecTest {
    private NetconfEventSourceManager netconfEventSourceManager;
    private ListenerRegistration<?> listenerRegistrationMock;
    private DOMMountPointService domMountPointServiceMock;
    private MountPointService mountPointServiceMock;
    private EventSourceRegistry eventSourceTopologyMock;
    private DataTreeModification<Node> dataTreeModificationMock;
    private RpcProviderService rpcProviderRegistryMock;
    private EventSourceRegistry eventSourceRegistry;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        final DataBroker dataBrokerMock = mock(DataBroker.class);
        final DOMNotificationPublishService domNotificationPublishServiceMock =
                mock(DOMNotificationPublishService.class);
        domMountPointServiceMock = mock(DOMMountPointService.class);
        eventSourceTopologyMock = mock(EventSourceRegistry.class);
        rpcProviderRegistryMock = mock(RpcProviderService.class);
        eventSourceRegistry = mock(EventSourceRegistry.class);

        listenerRegistrationMock = mock(ListenerRegistration.class);
        doReturn(listenerRegistrationMock).when(dataBrokerMock).registerDataTreeChangeListener(
                any(DataTreeIdentifier.class), any(NetconfEventSourceManager.class));

        DOMMountPoint domMountPointMock = mock(DOMMountPoint.class);
        Optional<DOMMountPoint> optionalDomMountServiceMock = Optional.of(domMountPointMock);
        doReturn(optionalDomMountServiceMock).when(domMountPointServiceMock).getMountPoint((YangInstanceIdentifier)
                notNull());
        DOMDataBroker mpDataBroker = mock(DOMDataBroker.class);
        doReturn(Optional.of(mpDataBroker)).when(domMountPointMock).getService(DOMDataBroker.class);
        doReturn(Optional.of(mock(DOMRpcService.class))).when(domMountPointMock).getService(DOMRpcService.class);
        doReturn(Optional.of(mock(DOMNotificationService.class))).when(domMountPointMock)
                .getService(DOMNotificationService.class);
        doReturn(Optional.of(mock(DOMSchemaService.class))).when(domMountPointMock).getService(DOMSchemaService.class);

        DOMDataTreeReadTransaction rtx = mock(DOMDataTreeReadTransaction.class);
        doReturn(rtx).when(mpDataBroker).newReadOnlyTransaction();
        final FluentFuture<Optional<NormalizedNode<?, ?>>> readStreamFuture =
                FluentFutures.immediateFluentFuture(Optional.of(NetconfTestUtils.getStreamsNode("stream-1")));

        YangInstanceIdentifier pathStream = YangInstanceIdentifier.builder().node(Netconf.QNAME).node(Streams.QNAME)
                .build();
        doReturn(readStreamFuture).when(rtx).read(LogicalDatastoreType.OPERATIONAL, pathStream);

        netconfEventSourceManager = new NetconfEventSourceManager(dataBrokerMock, SERIALIZER,
                domNotificationPublishServiceMock, domMountPointServiceMock, eventSourceRegistry);
        netconfEventSourceManager.setStreamMap(new HashMap<>());
    }

    @Test
    public void onDataChangedCreateEventSourceTestByCreateEntry() throws Exception {
        onDataChangedTestHelper(true, false, true, NetconfTestUtils.NOTIFICATION_CAPABILITY_PREFIX);
        netconfEventSourceManager.onDataTreeChanged(Collections.singletonList(dataTreeModificationMock));
        verify(eventSourceRegistry, times(1)).registerEventSource(any(EventSource.class));
    }

    @Test
    public void onDataChangedCreateEventSourceTestByUpdateEntry() throws Exception {
        onDataChangedTestHelper(false, true, true, NetconfTestUtils.NOTIFICATION_CAPABILITY_PREFIX);
        netconfEventSourceManager.onDataTreeChanged(Collections.singletonList(dataTreeModificationMock));
        verify(eventSourceRegistry, times(1)).registerEventSource(any(EventSource.class));
    }

    @Test
    public void onDataChangedCreateEventSourceTestNotNeconf() throws Exception {
        onDataChangedTestHelper(false, true, false, NetconfTestUtils.NOTIFICATION_CAPABILITY_PREFIX);
        netconfEventSourceManager.onDataTreeChanged(Collections.singletonList(dataTreeModificationMock));
        verify(eventSourceRegistry, times(0)).registerEventSource(any(EventSource.class));
    }

    @Test
    public void onDataChangedCreateEventSourceTestNotNotificationCapability() throws Exception {
        onDataChangedTestHelper(true, false, true, "bad-prefix");
        netconfEventSourceManager.onDataTreeChanged(Collections.singletonList(dataTreeModificationMock));
        verify(eventSourceRegistry, times(0)).registerEventSource(any(EventSource.class));
    }

    @SuppressWarnings("unchecked")
    private void onDataChangedTestHelper(final boolean create, final boolean update, final boolean isNetconf,
            final String notificationCapabilityPrefix) throws Exception {
        dataTreeModificationMock = mock(DataTreeModification.class);
        DataObjectModification<Node> mockModification = mock(DataObjectModification.class);
        doReturn(create ? DataObjectModification.ModificationType.WRITE :
            DataObjectModification.ModificationType.SUBTREE_MODIFIED).when(mockModification).getModificationType();
        doReturn(mockModification).when(dataTreeModificationMock).getRootNode();

        final Node node01;
        String nodeId = "Node01";
        if (isNetconf) {
            node01 = NetconfTestUtils
                    .getNetconfNode(nodeId, "node01.test.local", ConnectionStatus.Connected,
                            notificationCapabilityPrefix);

        } else {
            node01 = NetconfTestUtils.getNode(nodeId);
        }

        doReturn(node01).when(mockModification).getDataAfter();

        doReturn(DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL,
                NetconfTestUtils.getInstanceIdentifier(node01))).when(dataTreeModificationMock).getRootPath();
    }

}

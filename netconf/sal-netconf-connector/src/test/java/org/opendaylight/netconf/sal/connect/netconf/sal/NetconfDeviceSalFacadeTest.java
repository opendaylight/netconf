/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceSchema;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev221225.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yangtools.rfc8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceSalFacadeTest {
    private final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test", new InetSocketAddress("127.0.0.1", 8000));

    @Mock
    private NetconfDeviceSalProvider.MountInstance mountInstance;
    @Mock
    private NetconfDeviceSalProvider salProvider;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private TransactionChain txChain;
    @Mock
    private WriteTransaction tx;
    @Captor
    private ArgumentCaptor<NetconfNode> nodeCaptor;

    private NetconfDeviceSalFacade deviceFacade;

    @Before
    public void setUp() throws Exception {
        doReturn(txChain).when(dataBroker).createMergingTransactionChain(any());
        doReturn(tx).when(txChain).newWriteOnlyTransaction();
        doNothing().when(tx).mergeParentStructurePut(eq(LogicalDatastoreType.OPERATIONAL),
            eq(remoteDeviceId.getTopologyBindingPath().augmentation(NetconfNode.class)), nodeCaptor.capture());
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        final NetconfDeviceTopologyAdapter adapter = new NetconfDeviceTopologyAdapter(dataBroker, remoteDeviceId);

        deviceFacade = new NetconfDeviceSalFacade(remoteDeviceId, salProvider, dataBroker, "mockTopo");

        doReturn(adapter).when(salProvider).getTopologyDatastoreAdapter();

        doReturn(mountInstance).when(salProvider).getMountInstance();
        doNothing().when(mountInstance).onTopologyDeviceDisconnected();
    }

    @Test
    public void testOnDeviceDisconnected() {
        deviceFacade.onDeviceDisconnected();

        verifyConnectionStatusUpdate(ConnectionStatus.Connecting);
        verify(mountInstance, times(1)).onTopologyDeviceDisconnected();
    }

    @Test
    public void testOnDeviceFailed() {
        final Throwable throwable = new Throwable();
        deviceFacade.onDeviceFailed(throwable);

        verifyConnectionStatusUpdate(ConnectionStatus.UnableToConnect);
        verify(mountInstance, times(1)).onTopologyDeviceDisconnected();
    }

    @Test
    public void testOnDeviceClose() throws Exception {
        deviceFacade.close();
        verify(salProvider).close();
    }

    @Test
    public void testOnDeviceConnected() {
        final EffectiveModelContext schemaContext = mock(EffectiveModelContext.class);

        final var netconfSessionPreferences = NetconfSessionPreferences.fromStrings(
            List.of(NetconfMessageTransformUtil.NETCONF_CANDIDATE_URI.toString()));

        final var deviceServices = new RemoteDeviceServices(mock(Rpcs.Normalized.class), null);
        deviceFacade.onDeviceConnected(
            new NetconfDeviceSchema(NetconfDeviceCapabilities.empty(), new EmptyMountPointContext(schemaContext)),
            netconfSessionPreferences, deviceServices);

        verifyConnectionStatusUpdate(ConnectionStatus.Connected);
        verify(mountInstance, times(1)).onTopologyDeviceConnected(eq(schemaContext), eq(deviceServices),
            any(DOMDataBroker.class), any(NetconfDataTreeService.class));
    }

    @Test
    public void testOnDeviceNotification() throws Exception {
        final DOMNotification domNotification = mock(DOMNotification.class);
        deviceFacade.onNotification(domNotification);
        verify(mountInstance).publish(domNotification);
    }

    private void verifyConnectionStatusUpdate(final ConnectionStatus expectedStatus) {
        verify(tx).mergeParentStructurePut(eq(LogicalDatastoreType.OPERATIONAL),
            eq(remoteDeviceId.getTopologyBindingPath().augmentation(NetconfNode.class)), any());
        assertEquals(expectedStatus, nodeCaptor.getValue().getConnectionStatus());
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline.clustered;

import java.net.InetSocketAddress;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class TopologyMountPointFacadeTest {

    private static final RemoteDeviceId REMOTE_DEVICE_ID = new RemoteDeviceId("testing-device", new InetSocketAddress(9999));
    private static final String TOPOLOGY_ID = "testing-topology";

    @Mock
    Broker domBroker;

    @Mock
    BindingAwareBroker bindingBroker;

    @Mock
    RemoteDeviceHandler<NetconfSessionPreferences> connectionStatusListener1;

    @Mock
    RemoteDeviceHandler<NetconfSessionPreferences> connectionStatusListener2;


    private TopologyMountPointFacade mountPointFacade;

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);

        mountPointFacade = new TopologyMountPointFacade(TOPOLOGY_ID, REMOTE_DEVICE_ID, domBroker, bindingBroker);

        mountPointFacade.registerConnectionStatusListener(connectionStatusListener1);
        mountPointFacade.registerConnectionStatusListener(connectionStatusListener2);

    }

    @Test
    public void testOnDeviceConnected() {
        SchemaContext mockedContext = Mockito.mock(SchemaContext.class);
        NetconfSessionPreferences mockedPreferences = NetconfSessionPreferences.fromStrings(Collections.<String>emptyList());
        DOMRpcService mockedRpcService = Mockito.mock(DOMRpcService.class);
        mountPointFacade.onDeviceConnected(mockedContext, mockedPreferences, mockedRpcService);

        Mockito.verify(connectionStatusListener1).onDeviceConnected(mockedContext, mockedPreferences, mockedRpcService);
        Mockito.verify(connectionStatusListener2).onDeviceConnected(mockedContext, mockedPreferences, mockedRpcService);
    }

    @Test
    public void testOnDeviceDisconnected() {
        mountPointFacade.onDeviceDisconnected();

        Mockito.verify(connectionStatusListener1).onDeviceDisconnected();
        Mockito.verify(connectionStatusListener2).onDeviceDisconnected();
    }

    @Test
    public void testOnDeviceFailed() {
        Throwable mockedException = Mockito.mock(Throwable.class);
        mountPointFacade.onDeviceFailed(mockedException);

        Mockito.verify(connectionStatusListener1).onDeviceFailed(mockedException);
        Mockito.verify(connectionStatusListener2).onDeviceFailed(mockedException);
    }

}

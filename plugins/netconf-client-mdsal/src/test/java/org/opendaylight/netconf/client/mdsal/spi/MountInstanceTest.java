/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfService;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class MountInstanceTest {
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @Mock
    private DOMMountPointService service;
    @Mock
    private DOMDataBroker broker;
    @Mock
    private NetconfDataTreeService netconfService;
    @Mock
    private Rpcs.Normalized rpcService;
    @Mock
    private NetconfDeviceNotificationService notificationService;
    @Mock
    private DOMMountPointService.DOMMountPointBuilder mountPointBuilder;
    @Mock
    private ObjectRegistration<DOMMountPoint> registration;
    @Mock
    private DOMNotification notification;

    private NetconfDeviceMount mountInstance;

    @BeforeClass
    public static void suiteSetUp() throws Exception {
        SCHEMA_CONTEXT = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfService.class);
    }

    @Before
    public void setUp() throws Exception {
        when(service.createMountPoint(any(YangInstanceIdentifier.class))).thenReturn(mountPointBuilder);
        when(mountPointBuilder.register()).thenReturn(registration);

        mountInstance = new NetconfDeviceMount(
            new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830)),
            service, YangInstanceIdentifier.of());
    }

    @Test
    public void testOnTopologyDeviceConnected() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, new RemoteDeviceServices(rpcService, null),
            notificationService, broker, null);
        verify(mountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mountPointBuilder).addService(DOMDataBroker.class, broker);
        verify(mountPointBuilder).addService(DOMRpcService.class, rpcService);
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);
    }

    @Test
    public void testOnTopologyDeviceConnectedWithNetconfService() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, new RemoteDeviceServices(rpcService, null),
            notificationService, null, netconfService);
        verify(mountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mountPointBuilder).addService(NetconfDataTreeService.class, netconfService);
        verify(mountPointBuilder).addService(DOMRpcService.class, rpcService);
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);
    }

    @Test
    public void testOnTopologyDeviceDisconnected() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, new RemoteDeviceServices(rpcService, null),
            notificationService, broker, null);
        mountInstance.onDeviceDisconnected();
        verify(registration).close();
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, new RemoteDeviceServices(rpcService, null),
            notificationService, broker, null);
    }

    @Test
    public void testClose() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, new RemoteDeviceServices(rpcService, null),
            notificationService, broker, null);
        mountInstance.close();
        verify(registration).close();
    }

    @Test
    public void testPublishNotification() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, new RemoteDeviceServices(rpcService, null),
            notificationService, broker, null);
        verify(mountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);
        mountInstance.publish(notification);
        verify(notificationService).publishNotification(notification);
    }
}

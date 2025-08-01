/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.restconf.server.spi.CompositeServerStrategy;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class MountInstanceTest {
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @Mock
    private DOMMountPointService service;
    @Mock
    private DOMDataBroker broker;
    @Mock
    private DOMRpcService domRpcService;
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
    @Mock
    private ServerDataOperations dataOperarions;

    private NetconfDeviceMount mountInstance;

    @BeforeAll
    static void suiteSetUp() {
        SCHEMA_CONTEXT = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        doReturn(mountPointBuilder).when(service).createMountPoint(any(YangInstanceIdentifier.class));
        doReturn(registration).when(mountPointBuilder).register();
        doReturn(domRpcService).when(rpcService).domRpcService();

        mountInstance = new NetconfDeviceMount(
            new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830)),
            service, YangInstanceIdentifier.of());
    }

    @Test
    void testOnTopologyDeviceConnected() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, dataOperarions, new RemoteDeviceServices(rpcService, null),
            notificationService, broker);
        verify(mountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mountPointBuilder).addService(DOMDataBroker.class, broker);
        verify(mountPointBuilder).addService(DOMRpcService.class, rpcService.domRpcService());
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);
    }

    @Test
    void testOnTopologyDeviceConnectedWithNetconfService() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, dataOperarions, new RemoteDeviceServices(rpcService, null),
            notificationService, null);
        verify(mountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mountPointBuilder).addService(DOMRpcService.class, rpcService.domRpcService());
        final var strategyCaptor = ArgumentCaptor.forClass(DOMServerStrategy.class);
        verify(mountPointBuilder).addService(eq(DOMServerStrategy.class), strategyCaptor.capture());
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);

        final var serverStrategy = strategyCaptor.getValue();
        final var compositeStrategy = assertInstanceOf(CompositeServerStrategy.class, serverStrategy.serverStrategy());
        assertEquals(dataOperarions, compositeStrategy.data());
    }

    @Test
    void testOnTopologyDeviceDisconnected() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, dataOperarions, new RemoteDeviceServices(rpcService, null),
            notificationService, broker);
        mountInstance.onDeviceDisconnected();
        verify(registration).close();
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, dataOperarions, new RemoteDeviceServices(rpcService, null),
            notificationService, broker);
    }

    @Test
    void testClose() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, dataOperarions, new RemoteDeviceServices(rpcService, null),
            notificationService, broker);
        mountInstance.close();
        verify(registration).close();
    }

    @Test
    void testPublishNotification() {
        mountInstance.onDeviceConnected(SCHEMA_CONTEXT, dataOperarions, new RemoteDeviceServices(rpcService, null),
            notificationService, broker);
        verify(mountPointBuilder).addService(eq(DOMSchemaService.class), any());
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);
        mountInstance.publish(notification);
        verify(notificationService).publishNotification(notification);
    }
}

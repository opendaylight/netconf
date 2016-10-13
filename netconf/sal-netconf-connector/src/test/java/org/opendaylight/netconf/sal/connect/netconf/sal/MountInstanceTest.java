/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import java.net.InetSocketAddress;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class MountInstanceTest {

    private static SchemaContext SCHEMA_CONTEXT;

    @Mock
    private DOMMountPointService service;
    @Mock
    private DOMDataBroker broker;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private NetconfDeviceNotificationService notificationService;
    @Mock
    private DOMMountPointService.DOMMountPointBuilder mountPointBuilder;
    @Mock
    private ObjectRegistration<DOMMountPoint> registration;
    @Mock
    private DOMNotification notification;

    private NetconfDeviceSalProvider.MountInstance mountInstance;

    @BeforeClass
    public static void suiteSetUp() throws Exception{
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(
                Lists.newArrayList(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.$YangModuleInfoImpl.getInstance()));
        SCHEMA_CONTEXT = moduleInfoBackedContext.tryToCreateSchemaContext().get();

    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(service.createMountPoint(any(YangInstanceIdentifier.class))).thenReturn(mountPointBuilder);

        when(mountPointBuilder.register()).thenReturn(registration);
        mountInstance = new NetconfDeviceSalProvider.MountInstance(service, new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830)));
    }


    @Test
    public void testOnTopologyDeviceConnected() throws Exception {
        mountInstance.onTopologyDeviceConnected(SCHEMA_CONTEXT, broker, rpcService, notificationService);
        verify(mountPointBuilder).addInitialSchemaContext(SCHEMA_CONTEXT);
        verify(mountPointBuilder).addService(DOMDataBroker.class, broker);
        verify(mountPointBuilder).addService(DOMRpcService.class, rpcService);
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);
    }

    @Test
    public void testOnTopologyDeviceDisconnected() throws Exception {
        mountInstance.onTopologyDeviceConnected(SCHEMA_CONTEXT, broker, rpcService, notificationService);
        mountInstance.onTopologyDeviceDisconnected();
        verify(registration).close();
        try {
            mountInstance.onTopologyDeviceConnected(SCHEMA_CONTEXT, broker, rpcService, notificationService);
        } catch (final IllegalStateException e) {
            e.printStackTrace();
            Assert.fail("Topology registration still present after disconnect ");
        }
    }

    @Test
    public void testClose() throws Exception {
        mountInstance.onTopologyDeviceConnected(SCHEMA_CONTEXT, broker, rpcService, notificationService);
        mountInstance.close();
        verify(registration).close();
    }

    @Test
    public void testPublishNotification() throws Exception {
        mountInstance.onTopologyDeviceConnected(SCHEMA_CONTEXT, broker, rpcService, notificationService);
        verify(mountPointBuilder).addInitialSchemaContext(SCHEMA_CONTEXT);
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);
        mountInstance.publish(notification);
        verify(notificationService).publishNotification(notification);
    }


}
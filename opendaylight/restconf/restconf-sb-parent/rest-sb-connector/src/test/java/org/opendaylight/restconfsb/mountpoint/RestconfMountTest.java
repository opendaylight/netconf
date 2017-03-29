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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfMountTest {

    private static final RestconfDeviceId NODE_1 = new RestconfDeviceId("node1");
    @Mock
    private RestconfDevice device;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private ObjectRegistration<DOMMountPoint> registration;
    @Mock
    private DOMMountPointService.DOMMountPointBuilder mountPointBuilder;
    @Mock
    private SchemaContext schemaContext;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMNotificationService notificationService;
    @Mock
    private DOMRpcService rpcService;
    private RestconfMount mount;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(NODE_1).when(device).getDeviceId();
        doReturn(schemaContext).when(device).getSchemaContext();
        doReturn(dataBroker).when(device).getDataBroker();
        doReturn(notificationService).when(device).getNotificationService();
        doReturn(rpcService).when(device).getRpcService();
        doReturn(mountPointBuilder).when(mountPointService).createMountPoint(any(YangInstanceIdentifier.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addInitialSchemaContext(any(SchemaContext.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMDataBroker.class), any(DOMDataBroker.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMRpcService.class), any(DOMRpcService.class));
        doReturn(mountPointBuilder).when(mountPointBuilder).addService(eq(DOMNotificationService.class), any(DOMNotificationService.class));
        doReturn(registration).when(mountPointBuilder).register();
        doNothing().when(registration).close();
        doNothing().when(device).close();
        mount = new RestconfMount(device);
    }

    @Test
    public void testRegisterAndDeregister() throws Exception {
        mount.register(mountPointService);
        verify(mountPointBuilder).addInitialSchemaContext(schemaContext);
        verify(mountPointBuilder).addService(DOMDataBroker.class, dataBroker);
        verify(mountPointBuilder).addService(DOMRpcService.class, rpcService);
        verify(mountPointBuilder).addService(DOMNotificationService.class, notificationService);
        verify(mountPointBuilder).register();
        mount.deregister();
        verify(registration).close();
        verify(device).close();
    }


    @Test
    public void testClose() throws Exception {
        mount.register(mountPointService);
        verify(mountPointBuilder).register();
        mount.close();
        verify(registration).close();
        verify(device).close();
    }
}
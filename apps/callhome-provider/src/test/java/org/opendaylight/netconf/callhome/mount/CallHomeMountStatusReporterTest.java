/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.server.ssh.CallHomeSshSessionContextManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.DeviceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

@ExtendWith(MockitoExtension.class)
public class CallHomeMountStatusReporterTest {
    private static final String DEVICE_ID = "device_id";

    @Mock
    KeyedInstanceIdentifier<Device, DeviceKey> instanceIdentifier;
    @Mock
    CallHomeMountService mountService;
    @Mock
    DataBroker dataBroker;
    @Mock
    WriteTransaction writeTx;
    @Mock
    FluentFuture<Executor> fluentFuture;
    @Mock
    DeviceKey deviceKey;
    @Mock
    CallHomeSshSessionContextManager contextManager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        doReturn(null).when(dataBroker).registerDataTreeChangeListener(
            any(DataTreeIdentifier.class), any(DataTreeChangeListener.class));
        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(fluentFuture).when(writeTx).commit();
        doReturn(deviceKey).when(instanceIdentifier).getKey();
        doReturn(contextManager).when(mountService).createSshSessionContextManager();
        doReturn(DEVICE_ID).when(deviceKey).getUniqueId();
        doNothing().when(fluentFuture).addCallback(any(FutureCallback.class), any(Executor.class));
        doNothing().when(writeTx).delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
    }

    /**
     * Tests if device is disconnected when it is removed from allowed devices.
     */
    @Test
    void testSingleDeviceDeletion() {
        var reporter = new CallHomeMountStatusReporter(dataBroker, mountService);

        reporter.syncDeletedDevices(List.of(instanceIdentifier));
        verify(contextManager, times(1)).remove(DEVICE_ID);
    }

    /**
     * Tests if multiple devices are disconnected when they are removed from allowed devices.
     */
    @Test
    void testMultipleDevicesDeletion() {
        var reporter = new CallHomeMountStatusReporter(dataBroker, mountService);

        reporter.syncDeletedDevices(Collections.nCopies(5, instanceIdentifier));
        verify(contextManager, times(5)).remove(DEVICE_ID);
    }
}

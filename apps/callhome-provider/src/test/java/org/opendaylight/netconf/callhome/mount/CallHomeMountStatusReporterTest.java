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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

//@ExtendWith(MockitoExtension.class)
public class CallHomeMountStatusReporterTest {


    @Mock
    KeyedInstanceIdentifier<Device, DeviceKey> instanceIdentifier;
    @Mock
    CallHomeMountService mountService;
    @Mock
    DataBroker dataBroker;
    @Mock
    WriteTransaction writeTx;
    @Mock
    FluentFuture fluentFuture;
    @Mock
    DeviceKey deviceKey;
    @Mock
    CallHomeSshSessionContextManager contextManager;


    @BeforeEach
    void openMocks() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void test() {

        doReturn(null).when(dataBroker).registerDataTreeChangeListener(
            any(DataTreeIdentifier.class), any(DataTreeChangeListener.class));
        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(writeTx).delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
        doReturn(fluentFuture).when(writeTx).commit();
        doNothing().when(fluentFuture).addCallback(any(FutureCallback.class), any(Executor.class));
        doReturn(deviceKey).when(instanceIdentifier).getKey();
        doReturn(contextManager).when(mountService).createSshSessionContextManager();
        var reporter = new CallHomeMountStatusReporter(dataBroker, mountService);

        //test single deletion
        doReturn("id1").when(deviceKey).getUniqueId();
        reporter.syncDeletedDevices(List.of(instanceIdentifier));
        verify(contextManager, times(1)).remove("id1");

        //test multiple deletions
        doReturn("id2").when(deviceKey).getUniqueId();
        reporter.syncDeletedDevices(Collections.nCopies(5, instanceIdentifier));
        verify(contextManager, times(5)).remove("id2");





//        doReturn(null).when(writeTx).commit().addCallback(any(FutureCallback.class), any(Executor.class));

















//        InstanceIdentifier.create(NetworkTopology.class)
//            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)));

//        InstanceIdentifier.create()
//            .child(Device.class, new DeviceKey("id"));
//
//        KeyedInstanceIdentifier<Device,DeviceKey> instance = new KeyedInstanceIdentifier();
    }
}

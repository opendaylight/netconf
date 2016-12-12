/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.brocade.params.xml.ns.yang.zerotouch.callhome.server.rev161109.Devices;
import org.opendaylight.yang.gen.v1.urn.brocade.params.xml.ns.yang.zerotouch.callhome.server.rev161109.DevicesBuilder;
import org.opendaylight.yang.gen.v1.urn.brocade.params.xml.ns.yang.zerotouch.callhome.server.rev161109.devices.Device;
import org.opendaylight.yang.gen.v1.urn.brocade.params.xml.ns.yang.zerotouch.callhome.server.rev161109.devices.DeviceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.binding.util.BindingReflections;

@Ignore
public class CallHomeProviderTest extends AbstractDataBrokerTest{
    private final static InstanceIdentifier<Devices> ALL_DEVICES = InstanceIdentifier.builder(Devices.class).build();
    private IetfZeroTouchCallHomeServerProvider callHomeProvider;

    @Override
    protected Iterable<YangModuleInfo> getModuleInfos() throws Exception {
        return ImmutableSet.of(BindingReflections.getModuleInfo(Devices.class));
    }

    @Override
    protected void setupWithDataBroker(DataBroker dataBroker) {
        callHomeProvider = new IetfZeroTouchCallHomeServerProvider(dataBroker,Mockito.mock(CallHomeMountDispatcher.class));
        callHomeProvider.init();
    }

    @Test
    public void testCallHomeProvider() throws Exception{
        DataBroker broker = getDataBroker();
        WriteTransaction writeTransaction = broker.newWriteOnlyTransaction();

        String rsaStr = "AAAAB3NzaC1yc2EAAAADAQABAAABAQCvLigTfPZMqOQwHp051Co4lwwPwO21NFIXWgjQmCPEgRTqQpei7qQaxlLGkrIPjZtJQRgCuC+Sg8HFw1YpUaMybN0nFInInQLp/qe0yc9ByDZM2G86NX6W5W3+j87I8Fh1dnMov1iJ0DFVn8RLwdEGjreiZCRyJOMuHghh6y4EG7W8BwmZrse17zhSpc2wFOVhxeZnYAQFEw6g48LutFRDpoTjGgz1nz/L4zcaUxxigs8wdY+qTTOHxSTxlLqwSZPFLyYrV2KJ9mKahMuYUy6o2b8snsjvnSjyK0kY+U0C6c8fmPDFUc0RqJqfdnsIUyh11U8d3NZdaFWg0UW0SNK3";
        final String ecdsaStr = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBBSowqAx+rRuazzbe5/VRjC8YrIRI4reVWN13lzNE+LVxinUvPxP2yOGmANaREBRjFA1xHexSH4pgKNvkcQnRao=";

        final Device device = new DeviceBuilder()
                .setSshHostKey(ecdsaStr)
                .setUniqueId(ecdsaStr.toString())
                .build();
        Devices devices = new DevicesBuilder().setDevice(new ArrayList<Device>(){{add(device);}}).build();
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION,ALL_DEVICES, devices);
    }
}

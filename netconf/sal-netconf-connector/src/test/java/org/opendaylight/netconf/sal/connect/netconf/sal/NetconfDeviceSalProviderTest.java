/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceSalProviderTest {
    @Mock
    private DOMMountPointService mountPointService;

    private NetconfDeviceSalProvider provider;

    @Before
    public void setUp() {
        provider = new NetconfDeviceSalProvider(new RemoteDeviceId("device1",
                InetSocketAddress.createUnresolved("localhost", 17830)), mountPointService);
    }

    @Test
    public void close() {
        provider.close();
    }

    @Test
    public void closeWithoutNPE()  {
        close();

        // No further interations
        provider.close();
    }
}

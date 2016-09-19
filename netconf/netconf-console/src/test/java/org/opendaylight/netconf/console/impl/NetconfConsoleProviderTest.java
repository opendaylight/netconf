/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(FrameworkUtil.class)
public class NetconfConsoleProviderTest {

    @Test
    public void testProvider() throws Exception {
        final NetconfConsoleProvider netconfConsoleProvider = new NetconfConsoleProvider();

        PowerMockito.mockStatic(FrameworkUtil.class);

        final BindingAwareBroker.ProviderContext session = mock(BindingAwareBroker.ProviderContext.class);
        final MountPointService mountPointService = mock(MountPointService.class);
        final BundleContext bundleContext = mock(BundleContext.class);
        final DataBroker dataBroker = mock(DataBroker.class);
        final Bundle bundle = mock(Bundle.class);

        doReturn(dataBroker).when(session).getSALService(DataBroker.class);
        doReturn(mountPointService).when(session).getSALService(MountPointService.class);
        BDDMockito.given(FrameworkUtil.getBundle(any())).willReturn(bundle);
        when(bundle.getBundleContext()).thenReturn(bundleContext);

        netconfConsoleProvider.onSessionInitiated(session);

        verify(bundleContext, times(1)).registerService(eq(NetconfCommands.class), any(NetconfCommandsImpl.class), eq(null));

    }
}

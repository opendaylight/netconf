/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.Dictionary;
import java.util.Hashtable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;

public class NetconfConfigurationActivatorTest {

    @Mock
    private BundleContext context;
    @Mock
    private ServiceRegistration registration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(registration).when(context)
                .registerService(eq(ManagedService.class), any(NetconfConfiguration.class), any());
        doNothing().when(registration).unregister();
    }

    @Test
    public void testStartStop() throws Exception {
        final NetconfConfigurationActivator activator = new NetconfConfigurationActivator();
        activator.start(context);
        final Dictionary<String, String> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, "netconf");
        verify(context).registerService(eq(ManagedService.class), eq(NetconfConfiguration.getInstance()), eq(props));
        activator.stop(context);
        verify(registration).unregister();
    }

}
/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ManagedService;

public class NetconfConfigUtilTest {

    @Mock
    private ServiceReference<ManagedService> serviceRef;

    @Mock
    private ServiceReference<ManagedService> netconfConfigurationRef;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetNetconfConfigurationService() throws Exception {
        final Collection<ServiceReference<ManagedService>> services = new ArrayList<>();
        services.add(serviceRef);
        services.add(netconfConfigurationRef);
        final BundleContext context = mock(BundleContext.class);
        doReturn(services).when(context).getServiceReferences(ManagedService.class, null);
        final ManagedService service = mock(ManagedService.class);
        doReturn(service).when(context).getService(serviceRef);
        doReturn(NetconfConfiguration.getInstance()).when(context).getService(netconfConfigurationRef);
        final java.util.Optional<NetconfConfiguration> netconfConfigurationOptional =
                NetconfConfigUtil.getNetconfConfigurationService(context);
        Assert.assertTrue(netconfConfigurationOptional.isPresent());
        Assert.assertEquals(NetconfConfiguration.getInstance(), netconfConfigurationOptional.get());

    }

    @Test
    public void testGetNetconfConfigurationServiceAbsent() throws Exception {
        final Collection<ServiceReference<ManagedService>> services = new ArrayList<>();
        services.add(serviceRef);
        final BundleContext context = mock(BundleContext.class);
        doReturn(services).when(context).getServiceReferences(ManagedService.class, null);
        final ManagedService service = mock(ManagedService.class);
        doReturn(service).when(context).getService(serviceRef);
        final java.util.Optional<NetconfConfiguration> netconfConfigurationOptional =
                NetconfConfigUtil.getNetconfConfigurationService(context);
        Assert.assertFalse(netconfConfigurationOptional.isPresent());
    }

    @Test
    public void testGetNetconfConfigurationServiceInvalidSyntax() throws Exception {
        final BundleContext context = mock(BundleContext.class);
        final InvalidSyntaxException exception = new InvalidSyntaxException("Invalid syntax", "filter");
        doThrow(exception).when(context).getServiceReferences(ManagedService.class, null);
        final java.util.Optional<NetconfConfiguration> netconfConfigurationOptional =
                NetconfConfigUtil.getNetconfConfigurationService(context);
        Assert.assertFalse(netconfConfigurationOptional.isPresent());
    }
}

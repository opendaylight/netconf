/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.capability.BasicCapability;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class AggregatedNetconfOperationServiceFactoryTest {

    private final Set<Capability> factory1Caps = new HashSet<>();
    private final Set<Capability> factory2Caps = new HashSet<>();

    @Mock
    private CapabilityListener listener1;
    @Mock
    private CapabilityListener listener2;
    @Mock
    private CapabilityListener listener3;
    @Mock
    private NetconfOperationServiceFactory factory1;
    @Mock
    private NetconfOperationServiceFactory factory2;
    @Mock
    private AutoCloseable autoCloseable1;
    @Mock
    private AutoCloseable autoCloseable2;
    @Mock
    private AutoCloseable autoCloseable3;

    private AggregatedNetconfOperationServiceFactory aggregatedFactory;

    @Before
    public void setUp() throws Exception {
        factory1Caps.add(new BasicCapability("AAA"));
        factory1Caps.add(new BasicCapability("BBB"));

        factory2Caps.add(new BasicCapability("CCC"));
        factory2Caps.add(new BasicCapability("DDD"));

        aggregatedFactory = new AggregatedNetconfOperationServiceFactory();

        aggregatedFactory.registerCapabilityListener(listener1);
        aggregatedFactory.registerCapabilityListener(listener2);

        doReturn(autoCloseable1).when(factory1).registerCapabilityListener(listener1);
        doReturn(autoCloseable2).when(factory1).registerCapabilityListener(listener2);
        doReturn(factory1Caps).when(factory1).getCapabilities();

        doReturn(autoCloseable1).when(factory2).registerCapabilityListener(listener1);
        doReturn(autoCloseable2).when(factory2).registerCapabilityListener(listener2);
        doReturn(factory2Caps).when(factory2).getCapabilities();

        doNothing().when(autoCloseable1).close();
        doNothing().when(autoCloseable2).close();

        doReturn(autoCloseable3).when(factory1).registerCapabilityListener(listener3);
        doReturn(autoCloseable3).when(factory2).registerCapabilityListener(listener3);
    }

    @Test
    public void testOnAddAndOnRemove() throws Exception {
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory1);
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory2);

        verify(factory1).registerCapabilityListener(listener1);
        verify(factory2).registerCapabilityListener(listener1);
        verify(factory1).registerCapabilityListener(listener2);
        verify(factory2).registerCapabilityListener(listener2);

        aggregatedFactory.onRemoveNetconfOperationServiceFactory(factory1);
        aggregatedFactory.onRemoveNetconfOperationServiceFactory(factory2);

        verify(autoCloseable1, times(2)).close();
        verify(autoCloseable2, times(2)).close();
    }

    @Test
    public void testGetCapabilities() throws Exception {
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory1);
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory2);
        final Set<Capability> actual = aggregatedFactory.getCapabilities();
        Set<Capability> expected = Sets.union(factory1Caps, factory2Caps);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testRegisterCapabilityListener() throws Exception {
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory1);
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory2);
        aggregatedFactory.registerCapabilityListener(listener3);

        verify(factory1).registerCapabilityListener(listener3);
        verify(factory2).registerCapabilityListener(listener3);
    }

    @Test
    public void testClose() throws Exception {
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory1);
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory2);
        aggregatedFactory.close();
        verify(autoCloseable1, times(2)).close();
        verify(autoCloseable2, times(2)).close();
    }

}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.server.api.monitoring.BasicCapability;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.yangtools.concepts.Registration;

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
    private Registration reg1;
    @Mock
    private Registration reg2;
    @Mock
    private Registration reg3;

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

        doReturn(reg1).when(factory1).registerCapabilityListener(listener1);
        doReturn(reg2).when(factory1).registerCapabilityListener(listener2);
        doReturn(factory1Caps).when(factory1).getCapabilities();

        doReturn(reg1).when(factory2).registerCapabilityListener(listener1);
        doReturn(reg2).when(factory2).registerCapabilityListener(listener2);
        doReturn(factory2Caps).when(factory2).getCapabilities();

        doNothing().when(reg1).close();
        doNothing().when(reg2).close();

        doReturn(reg3).when(factory1).registerCapabilityListener(listener3);
        doReturn(reg3).when(factory2).registerCapabilityListener(listener3);
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

        verify(reg1, times(2)).close();
        verify(reg2, times(2)).close();
    }

    @Test
    public void testGetCapabilities() throws Exception {
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory1);
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory2);
        final Set<Capability> actual = aggregatedFactory.getCapabilities();
        Set<Capability> expected = Sets.union(factory1Caps, factory2Caps);
        assertEquals(expected, actual);
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
        verify(reg1, times(2)).close();
        verify(reg2, times(2)).close();
    }
}

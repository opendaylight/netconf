/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.controller.config.util.capability.BasicCapability;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;


public class AggregatedNetconfOperationServiceFactoryTest {

    private Set<Capability> factory1Caps = new HashSet<>();
    private Set<Capability> factory2Caps = new HashSet<>();

    private final CapabilityListener listener1 = mock(CapabilityListener.class);
    private final CapabilityListener listener2  = mock(CapabilityListener.class);
    private final NetconfOperationServiceFactory factory1 = getMock(NetconfOperationServiceFactory.class);;
    private final NetconfOperationServiceFactory factory2 = getMock(NetconfOperationServiceFactory.class);;
    private final AutoCloseable autoCloseable1 = getMock(AutoCloseable.class);
    private final AutoCloseable autoCloseable2 = getMock(AutoCloseable.class);

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

        when(factory1.registerCapabilityListener(listener1)).thenReturn(autoCloseable1);
        when(factory1.registerCapabilityListener(listener2)).thenReturn(autoCloseable2);
        when(factory1.getCapabilities()).thenReturn(factory1Caps);

        when(factory2.registerCapabilityListener(listener1)).thenReturn(autoCloseable1);
        when(factory2.registerCapabilityListener(listener2)).thenReturn(autoCloseable2);
        when(factory2.getCapabilities()).thenReturn(factory2Caps);

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
        CapabilityListener listener = mock(CapabilityListener.class);
        aggregatedFactory.registerCapabilityListener(listener);

        verify(factory1).registerCapabilityListener(listener);
        verify(factory2).registerCapabilityListener(listener);
    }

    @Test
    public void testClose() throws Exception {
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory1);
        aggregatedFactory.onAddNetconfOperationServiceFactory(factory2);
        aggregatedFactory.close();
        verify(autoCloseable1, times(2)).close();
        verify(autoCloseable2, times(2)).close();
    }

    private <T> T getMock(Class<T> cls) {
        return mock(cls, new Answer() {
            @Override
            public T answer(InvocationOnMock invocation) throws Throwable {
                return null;
            }
        });
    }
}
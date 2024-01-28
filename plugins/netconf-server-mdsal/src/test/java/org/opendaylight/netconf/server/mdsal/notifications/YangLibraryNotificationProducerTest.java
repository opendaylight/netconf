/*
 * Copyright (c) 2020 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.netconf.server.api.notifications.YangLibraryPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryChangeBuilder;
import org.opendaylight.yangtools.concepts.Registration;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class YangLibraryNotificationProducerTest {
    @Mock
    private YangLibraryPublisherRegistration yangLibraryPublisherRegistration;
    @Mock
    private NetconfNotificationCollector netconfNotificationCollector;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private Registration registration;

    private YangLibraryNotificationProducer yangLibraryNotificationProducer;

    @Before
    public void setUp() {
        doNothing().when(yangLibraryPublisherRegistration).onYangLibraryChange(any(YangLibraryChange.class));
        doReturn(yangLibraryPublisherRegistration).when(netconfNotificationCollector)
                .registerYangLibraryPublisher();
        doReturn(registration).when(dataBroker).registerDataListener(any(), any());

        yangLibraryNotificationProducer = new YangLibraryNotificationProducer(netconfNotificationCollector, dataBroker);
    }

    @Test
    public void testOnDataTreeChanged() {
        final String moduleSetId = "1";
        final var modulesStateAfter = new ModulesStateBuilder().setModuleSetId(moduleSetId).build();

        yangLibraryNotificationProducer.dataChangedTo(modulesStateAfter);

        final var yangLibraryChange = new YangLibraryChangeBuilder().setModuleSetId(moduleSetId).build();
        verify(yangLibraryPublisherRegistration).onYangLibraryChange(yangLibraryChange);
    }
}
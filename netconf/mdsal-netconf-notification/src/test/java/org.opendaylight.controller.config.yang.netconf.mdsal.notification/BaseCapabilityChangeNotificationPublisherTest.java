/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.ChangedByBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.changed.by.server.or.user.ServerBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class BaseCapabilityChangeNotificationPublisherTest {

    @Mock
    private BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doNothing().when(baseNotificationPublisherRegistration).onCapabilityChanged(any(NetconfCapabilityChange.class));
    }

    @Test
    public void testOnDataChanged() {
        final BaseCapabilityChangeNotificationPublisher baseCapabilityChangeNotificationPublisher =
                new BaseCapabilityChangeNotificationPublisher(baseNotificationPublisherRegistration);
        final InstanceIdentifier capabilitiesIdentifier = InstanceIdentifier.create(NetconfState.class).child(Capabilities.class).builder().build();
        final List<Uri> newCapabilitiesList = Lists.newArrayList(new Uri("newCapability"), new Uri("createdCapability"));

        AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> capabilitiesChange = mock(AsyncDataChangeEvent.class);
        Capabilities newCapabilities = new CapabilitiesBuilder().setCapability(newCapabilitiesList).build();
        Map<InstanceIdentifier<?>, DataObject> createdData = Maps.newHashMap();
        createdData.put(capabilitiesIdentifier, newCapabilities);
        doReturn(createdData).when(capabilitiesChange).getCreatedData();
        baseCapabilityChangeNotificationPublisher.onDataChanged(capabilitiesChange);

        verify(baseNotificationPublisherRegistration).onCapabilityChanged(changedCapabilitesFrom(newCapabilitiesList, Collections.<Uri>emptyList()));

        final List<Uri> originalCapabilitiesList = Lists.newArrayList(new Uri("originalCapability"), new Uri("anotherOriginalCapability"));
        final List<Uri> updatedCapabilitiesList = Lists.newArrayList(new Uri("originalCapability"), new Uri("newCapability"));

        Capabilities originalCapabilities = new CapabilitiesBuilder().setCapability(originalCapabilitiesList).build();
        Capabilities updatedCapabilities = new CapabilitiesBuilder().setCapability(updatedCapabilitiesList).build();

        doReturn(Collections.emptyMap()).when(capabilitiesChange).getCreatedData();
        doReturn(originalCapabilities).when(capabilitiesChange).getOriginalSubtree();
        doReturn(updatedCapabilities).when(capabilitiesChange).getUpdatedSubtree();
        baseCapabilityChangeNotificationPublisher.onDataChanged(capabilitiesChange);

        verify(baseNotificationPublisherRegistration).onCapabilityChanged(changedCapabilitesFrom(
                Lists.newArrayList(new Uri("newCapability")), Lists.newArrayList(new Uri("anotherOriginalCapability"))));
    }

    private NetconfCapabilityChange changedCapabilitesFrom(List<Uri> added, List<Uri> deleted) {
        NetconfCapabilityChangeBuilder netconfCapabilityChangeBuilder = new NetconfCapabilityChangeBuilder();
        netconfCapabilityChangeBuilder.setChangedBy(new ChangedByBuilder().setServerOrUser(
                new ServerBuilder().setServer(true).build()).build());

        netconfCapabilityChangeBuilder.setModifiedCapability(Collections.<Uri>emptyList());
        netconfCapabilityChangeBuilder.setAddedCapability(added);
        netconfCapabilityChangeBuilder.setDeletedCapability(deleted);

        return netconfCapabilityChangeBuilder.build();
    }
}

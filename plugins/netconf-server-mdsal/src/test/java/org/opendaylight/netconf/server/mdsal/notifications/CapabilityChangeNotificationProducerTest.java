/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationCollector;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.ChangedByBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.changed.by.server.or.user.ServerBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class CapabilityChangeNotificationProducerTest {
    @Mock
    private BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;
    @Mock
    private ListenerRegistration<?> listenerRegistration;
    @Mock
    private NetconfNotificationCollector netconfNotificationCollector;
    @Mock
    private DataBroker dataBroker;

    private CapabilityChangeNotificationProducer capabilityChangeNotificationProducer;

    @Before
    public void setUp() {
        doReturn(listenerRegistration).when(dataBroker).registerDataTreeChangeListener(any(DataTreeIdentifier.class),
                any(DataTreeChangeListener.class));

        doNothing().when(baseNotificationPublisherRegistration).onCapabilityChanged(any(NetconfCapabilityChange.class));

        doReturn(baseNotificationPublisherRegistration).when(netconfNotificationCollector)
                .registerBaseNotificationPublisher();

        capabilityChangeNotificationProducer = new CapabilityChangeNotificationProducer(netconfNotificationCollector,
                dataBroker);
    }

    @Test
    public void testOnDataChangedCreate() {
        final InstanceIdentifier<Capabilities> capabilitiesIdentifier =
                InstanceIdentifier.create(NetconfState.class).child(Capabilities.class);
        final Set<Uri> newCapabilitiesList = Set.of(new Uri("newCapability"), new Uri("createdCapability"));
        Capabilities newCapabilities = new CapabilitiesBuilder().setCapability(newCapabilitiesList).build();
        Map<InstanceIdentifier<?>, DataObject> createdData = new HashMap<>();
        createdData.put(capabilitiesIdentifier, newCapabilities);
        verifyDataTreeChange(DataObjectModification.ModificationType.WRITE, null, newCapabilities,
                changedCapabilitesFrom(newCapabilitiesList, Set.of()));
    }

    @Test
    public void testOnDataChangedUpdate() {
        Capabilities originalCapabilities = new CapabilitiesBuilder()
            .setCapability(Set.of(new Uri("originalCapability"), new Uri("anotherOriginalCapability")))
            .build();
        Capabilities updatedCapabilities = new CapabilitiesBuilder()
            .setCapability(Set.of(new Uri("originalCapability"), new Uri("newCapability")))
            .build();
        verifyDataTreeChange(DataObjectModification.ModificationType.WRITE, originalCapabilities, updatedCapabilities,
            changedCapabilitesFrom(Set.of(new Uri("newCapability")), Set.of(new Uri("anotherOriginalCapability"))));
    }

    @Test
    public void testOnDataChangedDelete() {
        final Set<Uri> originalCapabilitiesList =
            Set.of(new Uri("originalCapability"), new Uri("anotherOriginalCapability"));
        final Capabilities originalCapabilities =
            new CapabilitiesBuilder().setCapability(originalCapabilitiesList).build();
        verifyDataTreeChange(DataObjectModification.ModificationType.DELETE, originalCapabilities, null,
            changedCapabilitesFrom(Set.of(), originalCapabilitiesList));
    }

    @SuppressWarnings("unchecked")
    private void verifyDataTreeChange(final DataObjectModification.ModificationType modificationType,
                                      final Capabilities originalCapabilities, final Capabilities updatedCapabilities,
                                      final NetconfCapabilityChange expectedChange) {
        final DataTreeModification<Capabilities> treeChange2 = mock(DataTreeModification.class);
        final DataObjectModification<Capabilities> objectChange2 = mock(DataObjectModification.class);
        doReturn(modificationType).when(objectChange2).getModificationType();
        doReturn(objectChange2).when(treeChange2).getRootNode();
        doReturn(originalCapabilities).when(objectChange2).getDataBefore();
        doReturn(updatedCapabilities).when(objectChange2).getDataAfter();
        capabilityChangeNotificationProducer.onDataTreeChanged(List.of(treeChange2));
        verify(baseNotificationPublisherRegistration).onCapabilityChanged(expectedChange);
    }

    private static NetconfCapabilityChange changedCapabilitesFrom(final Set<Uri> added, final Set<Uri> deleted) {
        NetconfCapabilityChangeBuilder netconfCapabilityChangeBuilder = new NetconfCapabilityChangeBuilder();
        netconfCapabilityChangeBuilder.setChangedBy(new ChangedByBuilder().setServerOrUser(
                new ServerBuilder().setServer(Empty.value()).build()).build());

        netconfCapabilityChangeBuilder.setModifiedCapability(Set.of());
        netconfCapabilityChangeBuilder.setAddedCapability(added);
        netconfCapabilityChangeBuilder.setDeletedCapability(deleted);

        return netconfCapabilityChangeBuilder.build();
    }
}

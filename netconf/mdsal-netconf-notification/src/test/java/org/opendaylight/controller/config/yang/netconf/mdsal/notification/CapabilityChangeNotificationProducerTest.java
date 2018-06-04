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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.notifications.NetconfNotificationCollector;
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

public class CapabilityChangeNotificationProducerTest {

    private CapabilityChangeNotificationProducer capabilityChangeNotificationProducer;

    @Mock
    private BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;
    @Mock
    private ListenerRegistration listenerRegistration;

    @Mock
    private NetconfNotificationCollector netconfNotificationCollector;
    @Mock
    private DataBroker dataBroker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

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
        final List<Uri> newCapabilitiesList =
                Lists.newArrayList(new Uri("newCapability"), new Uri("createdCapability"));
        Capabilities newCapabilities = new CapabilitiesBuilder().setCapability(newCapabilitiesList).build();
        Map<InstanceIdentifier<?>, DataObject> createdData = Maps.newHashMap();
        createdData.put(capabilitiesIdentifier, newCapabilities);
        verifyDataTreeChange(DataObjectModification.ModificationType.WRITE, null, newCapabilities,
                changedCapabilitesFrom(newCapabilitiesList, Collections.<Uri>emptyList()));
    }

    @Test
    public void testOnDataChangedUpdate() {
        final List<Uri> originalCapabilitiesList =
                Lists.newArrayList(new Uri("originalCapability"), new Uri("anotherOriginalCapability"));
        final List<Uri> updatedCapabilitiesList =
                Lists.newArrayList(new Uri("originalCapability"), new Uri("newCapability"));
        Capabilities originalCapabilities = new CapabilitiesBuilder().setCapability(originalCapabilitiesList).build();
        Capabilities updatedCapabilities = new CapabilitiesBuilder().setCapability(updatedCapabilitiesList).build();
        verifyDataTreeChange(DataObjectModification.ModificationType.WRITE, originalCapabilities,
                updatedCapabilities, changedCapabilitesFrom(
                Lists.newArrayList(new Uri("newCapability")), Lists.newArrayList(new Uri("anotherOriginalCapability")
                        )));
    }

    @Test
    public void testOnDataChangedDelete() {
        final List<Uri> originalCapabilitiesList = Lists.newArrayList(new Uri("originalCapability"),
                new Uri("anotherOriginalCapability"));
        final Capabilities originalCapabilities =
                new CapabilitiesBuilder().setCapability(originalCapabilitiesList).build();
        verifyDataTreeChange(DataObjectModification.ModificationType.DELETE, originalCapabilities, null,
                changedCapabilitesFrom(Collections.emptyList(), originalCapabilitiesList));
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
        capabilityChangeNotificationProducer.onDataTreeChanged(Collections.singleton(treeChange2));
        verify(baseNotificationPublisherRegistration).onCapabilityChanged(expectedChange);
    }

    private NetconfCapabilityChange changedCapabilitesFrom(final List<Uri> added, final List<Uri> deleted) {
        NetconfCapabilityChangeBuilder netconfCapabilityChangeBuilder = new NetconfCapabilityChangeBuilder();
        netconfCapabilityChangeBuilder.setChangedBy(new ChangedByBuilder().setServerOrUser(
                new ServerBuilder().setServer(true).build()).build());

        netconfCapabilityChangeBuilder.setModifiedCapability(Collections.<Uri>emptyList());
        netconfCapabilityChangeBuilder.setAddedCapability(added);
        netconfCapabilityChangeBuilder.setDeletedCapability(deleted);

        return netconfCapabilityChangeBuilder.build();
    }
}

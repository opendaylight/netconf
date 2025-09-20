/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectDeleted;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
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
import org.opendaylight.yangtools.binding.DataObject;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.ExactDataObjectStep;
import org.opendaylight.yangtools.binding.NodeStep;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;

@ExtendWith(MockitoExtension.class)
class CapabilityChangeNotificationProducerTest {
    @Mock
    private BaseNotificationPublisherRegistration baseNotificationPublisherRegistration;
    @Mock
    private Registration listenerRegistration;
    @Mock
    private NetconfNotificationCollector netconfNotificationCollector;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private DataTreeModification<Capabilities> treeModification;

    private CapabilityChangeNotificationProducer capabilityChangeNotificationProducer;

    @BeforeEach
    void setUp() {
        doReturn(listenerRegistration).when(dataBroker).registerTreeChangeListener(eq(LogicalDatastoreType.OPERATIONAL),
            any(DataObjectIdentifier.class), any(DataTreeChangeListener.class));

        doNothing().when(baseNotificationPublisherRegistration).onCapabilityChanged(any(NetconfCapabilityChange.class));

        doReturn(baseNotificationPublisherRegistration).when(netconfNotificationCollector)
                .registerBaseNotificationPublisher();

        capabilityChangeNotificationProducer = new CapabilityChangeNotificationProducer(netconfNotificationCollector,
                dataBroker);
    }

    @Test
    void testOnDataChangedCreate() {
        final var capabilitiesIdentifier = DataObjectIdentifier.builder(NetconfState.class).child(Capabilities.class)
            .build();
        final var newCapabilitiesList = Set.of(new Uri("newCapability"), new Uri("createdCapability"));
        final var newCapabilities = new CapabilitiesBuilder().setCapability(newCapabilitiesList).build();
        final var createdData = new HashMap<DataObjectIdentifier<?>, DataObject>();
        createdData.put(capabilitiesIdentifier, newCapabilities);
        verifyDataTreeChange(null, newCapabilities, changedCapabilitesFrom(newCapabilitiesList, Set.of()));
    }

    @Test
    void testOnDataChangedUpdate() {
        Capabilities originalCapabilities = new CapabilitiesBuilder()
            .setCapability(Set.of(new Uri("originalCapability"), new Uri("anotherOriginalCapability")))
            .build();
        Capabilities updatedCapabilities = new CapabilitiesBuilder()
            .setCapability(Set.of(new Uri("originalCapability"), new Uri("newCapability")))
            .build();
        verifyDataTreeChange(originalCapabilities, updatedCapabilities,
            changedCapabilitesFrom(Set.of(new Uri("newCapability")), Set.of(new Uri("anotherOriginalCapability"))));
    }

    @Test
    void testOnDataChangedDelete() {
        final var originalCapabilitiesList =
            Set.of(new Uri("originalCapability"), new Uri("anotherOriginalCapability"));
        final var originalCapabilities = new CapabilitiesBuilder().setCapability(originalCapabilitiesList).build();
        doReturn(new DataObjectDeleted<Capabilities>() {
            @Override
            public ExactDataObjectStep<Capabilities> step() {
                return new NodeStep<>(Capabilities.class);
            }

            @Override
            public Capabilities dataBefore() {
                return originalCapabilities;
            }

            @Override
            public <C extends DataObject> DataObjectModification<C> modifiedChild(final ExactDataObjectStep<C> step) {
                return null;
            }

            @Override
            public Collection<? extends DataObjectModification<?>> modifiedChildren() {
                return List.of();
            }

            @Override
            protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
                return helper;
            }
        }).when(treeModification).getRootNode();
        capabilityChangeNotificationProducer.onDataTreeChanged(List.of(treeModification));
        verify(baseNotificationPublisherRegistration)
            .onCapabilityChanged(changedCapabilitesFrom(Set.of(), originalCapabilitiesList));
    }

    private void verifyDataTreeChange(final Capabilities originalCapabilities, final Capabilities updatedCapabilities,
            final NetconfCapabilityChange expectedChange) {
        doReturn(new DataObjectWritten<Capabilities>() {
            @Override
            public Capabilities dataAfter() {
                return updatedCapabilities;
            }

            @Override
            public ExactDataObjectStep<Capabilities> step() {
                return new NodeStep<>(Capabilities.class);
            }

            @Override
            public Capabilities dataBefore() {
                return originalCapabilities;
            }

            @Override
            public <C extends DataObject> DataObjectModification<C> modifiedChild(final ExactDataObjectStep<C> step) {
                return null;
            }

            @Override
            public Collection<? extends DataObjectModification<?>> modifiedChildren() {
                return List.of();
            }

            @Override
            protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
                return helper;
            }
        }).when(treeModification).getRootNode();
        capabilityChangeNotificationProducer.onDataTreeChanged(List.of(treeModification));
        verify(baseNotificationPublisherRegistration).onCapabilityChanged(expectedChange);
    }

    private static NetconfCapabilityChange changedCapabilitesFrom(final Set<Uri> added, final Set<Uri> deleted) {
        return new NetconfCapabilityChangeBuilder()
            .setChangedBy(new ChangedByBuilder()
                .setServerOrUser(new ServerBuilder().setServer(Empty.value()).build())
                .build())
            .setModifiedCapability(Set.of())
            .setAddedCapability(added)
            .setDeletedCapability(deleted)
            .build();
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.server.api.monitoring.BasicCapability;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SchemasBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.SchemaKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.ChangedByBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.changed.by.parms.changed.by.server.or.user.ServerBuilder;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;

final class NetconfCapabilityMonitoringService implements CapabilityListener, AutoCloseable {
    private static final Schema.Location NETCONF_LOCATION = new Schema.Location(Schema.Location.Enumeration.NETCONF);
    private static final Set<Schema.Location> NETCONF_LOCATIONS = Set.of(NETCONF_LOCATION);
    private static final BasicCapability CANDIDATE_CAPABILITY = new BasicCapability(CapabilityURN.CANDIDATE);
    // FIXME: hard-coded scheme here
    private static final BasicCapability URL_CAPABILITY = new BasicCapability(CapabilityURN.URL + "?scheme=file");
    private static final Function<Capability, Uri> CAPABILITY_TO_URI = input -> new Uri(input.getCapabilityUri());

    private final NetconfOperationServiceFactory netconfOperationProvider;
    private final Map<Uri, Capability> capabilities = new HashMap<>();
    private final Map<String, Map<String, String>> mappedModulesToRevisionToSchema = new HashMap<>();

    private final Set<NetconfMonitoringService.CapabilitiesListener> listeners = new HashSet<>();
    private volatile BaseNotificationPublisherRegistration notificationPublisher;

    NetconfCapabilityMonitoringService(final NetconfOperationServiceFactory netconfOperationProvider) {
        this.netconfOperationProvider = netconfOperationProvider;
        netconfOperationProvider.registerCapabilityListener(this);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    synchronized Schemas getSchemas() {
        try {
            return transformSchemas(netconfOperationProvider.getCapabilities());
        } catch (final Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new IllegalStateException("Exception while closing", e);
        }
    }

    synchronized String getSchemaForModuleRevision(final String moduleName, final Optional<String> revision) {
        final Map<String, String> revisionMapRequest = mappedModulesToRevisionToSchema.get(moduleName);
        checkState(revisionMapRequest != null,
                "Capability for module %s not present, available modules : %s",
                moduleName, Collections2.transform(capabilities.values(), CAPABILITY_TO_URI));

        final String revToLookup = revision.orElse("");
        final String schema = revisionMapRequest.get(revToLookup);
        if (schema != null) {
            // Exact match found
            return schema;
        }

        // We can recover only if the revision was not specified
        checkState(revision.isEmpty(),
            "Capability for module %s:%s not present, available revisions for module: %s", moduleName,
            revToLookup, revisionMapRequest.keySet());

        checkState(revisionMapRequest.size() == 1,
                "Expected 1 capability for module %s, available revisions : %s", moduleName,
                revisionMapRequest.keySet());
        // Only one revision is present, so return it
        return revisionMapRequest.values().iterator().next();
    }

    private void updateCapabilityToSchemaMap(final Set<Capability> added, final Set<Capability> removed) {
        for (final Capability cap : added) {
            if (isValidModuleCapability(cap)) {
                mappedModulesToRevisionToSchema.computeIfAbsent(cap.getModuleName().orElseThrow(), k -> new HashMap<>())
                    .put(cap.getRevision().orElse(""), cap.getCapabilitySchema().orElseThrow());
            }
        }
        for (final Capability cap : removed) {
            if (isValidModuleCapability(cap)) {
                final Map<String, String> revisionMap =
                    mappedModulesToRevisionToSchema.get(cap.getModuleName().orElseThrow());
                if (revisionMap != null) {
                    revisionMap.remove(cap.getRevision().orElseThrow());
                    if (revisionMap.isEmpty()) {
                        mappedModulesToRevisionToSchema.remove(cap.getModuleName().orElseThrow());
                    }
                }
            }
        }
    }

    private static boolean isValidModuleCapability(final Capability cap) {
        return cap.getModuleName().isPresent() && cap.getCapabilitySchema().isPresent();
    }

    synchronized Capabilities getCapabilities() {
        return new CapabilitiesBuilder().setCapability(Set.copyOf(capabilities.keySet())).build();
    }

    synchronized Registration registerListener(final NetconfMonitoringService.CapabilitiesListener listener) {
        listeners.add(listener);
        listener.onCapabilitiesChanged(getCapabilities());
        listener.onSchemasChanged(getSchemas());
        return new AbstractRegistration() {
            @Override
            protected void removeRegistration() {
                synchronized (NetconfCapabilityMonitoringService.this) {
                    listeners.remove(listener);
                }
            }
        };
    }

    private static Schemas transformSchemas(final Set<Capability> caps) {
        final Map<SchemaKey, Schema> schemas = Maps.newHashMapWithExpectedSize(caps.size());
        for (final Capability cap : caps) {
            if (isValidModuleCapability(cap)) {
                final SchemaKey key = new SchemaKey(Yang.VALUE, cap.getModuleName().orElseThrow(),
                    cap.getRevision().orElse(""));
                schemas.put(key, new SchemaBuilder()
                    .withKey(key)
                    .setNamespace(new Uri(cap.getModuleNamespace().orElseThrow()))
                    .setLocation(transformLocations(cap.getLocation()))
                    .build());
            }
        }

        return new SchemasBuilder().setSchema(schemas).build();
    }

    private static Set<Schema.Location> transformLocations(final Collection<String> locations) {
        if (locations.isEmpty()) {
            return NETCONF_LOCATIONS;
        }

        final var b = ImmutableSet.<Schema.Location>builder();
        b.add(NETCONF_LOCATION);

        for (final String location : locations) {
            b.add(new Schema.Location(new Uri(location)));
        }

        return b.build();
    }

    private static Set<Capability> setupCapabilities(final Set<Capability> caps) {
        Set<Capability> capabilities = new HashSet<>(caps);
        capabilities.add(CANDIDATE_CAPABILITY);
        capabilities.add(URL_CAPABILITY);
        // FIXME: rollback on error not supported EditConfigXmlParser:100
        // [RFC6241] 8.5.  Rollback-on-Error Capability
        // capabilities.add(new BasicCapability(CapabilityURN.ROLLBACK_ON_ERROR));
        return capabilities;
    }

    @Override
    public synchronized void close() {
        listeners.clear();
        capabilities.clear();
    }

    @Override
    public synchronized void onCapabilitiesChanged(final Set<Capability> added, final Set<Capability> removed) {
        onCapabilitiesAdded(added);
        onCapabilitiesRemoved(removed);
        updateCapabilityToSchemaMap(added, removed);
        notifyCapabilityChanged(getCapabilities());

        // publish notification to notification collector about changed capabilities
        if (notificationPublisher != null) {
            notificationPublisher.onCapabilityChanged(computeDiff(added, removed));
        }
    }

    private void notifyCapabilityChanged(final Capabilities newCapabilities) {
        for (NetconfMonitoringService.CapabilitiesListener listener : listeners) {
            listener.onCapabilitiesChanged(newCapabilities);
            listener.onSchemasChanged(getSchemas());
        }
    }


    private static NetconfCapabilityChange computeDiff(final Set<Capability> added, final Set<Capability> removed) {
        return new NetconfCapabilityChangeBuilder()
            .setChangedBy(new ChangedByBuilder()
                .setServerOrUser(new ServerBuilder().setServer(Empty.value()).build())
                .build())
            .setDeletedCapability(Set.copyOf(Collections2.transform(removed, CAPABILITY_TO_URI)))
            .setAddedCapability(Set.copyOf(Collections2.transform(added, CAPABILITY_TO_URI)))
            // TODO modified should be computed ... but why ?
            .setModifiedCapability(Set.of())
            .build();
    }


    private void onCapabilitiesAdded(final Set<Capability> addedCaps) {
        capabilities.putAll(Maps.uniqueIndex(setupCapabilities(addedCaps), CAPABILITY_TO_URI));
    }

    private void onCapabilitiesRemoved(final Set<Capability> removedCaps) {
        for (final Capability addedCap : removedCaps) {
            capabilities.remove(new Uri(addedCap.getCapabilityUri()));
        }
    }

    void setNotificationPublisher(final BaseNotificationPublisherRegistration notificationPublisher) {
        this.notificationPublisher = notificationPublisher;
    }
}

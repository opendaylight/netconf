/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opendaylight.controller.config.util.capability.BasicCapability;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
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

class NetconfCapabilityMonitoringService implements CapabilityListener, AutoCloseable {

    private static final Schema.Location NETCONF_LOCATION = new Schema.Location(Schema.Location.Enumeration.NETCONF);
    private static final List<Schema.Location> NETCONF_LOCATIONS = ImmutableList.of(NETCONF_LOCATION);
    private static final BasicCapability CANDIDATE_CAPABILITY =
            new BasicCapability("urn:ietf:params:netconf:capability:candidate:1.0");
    private static final Function<Capability, Uri> CAPABILITY_TO_URI = input -> new Uri(input.getCapabilityUri());

    private final NetconfOperationServiceFactory netconfOperationProvider;
    private final Map<Uri, Capability> capabilities = Maps.newHashMap();
    private final Map<String, Map<String, String>> mappedModulesToRevisionToSchema = Maps.newHashMap();


    private final Set<NetconfMonitoringService.CapabilitiesListener> listeners = Sets.newHashSet();
    private volatile BaseNotificationPublisherRegistration notificationPublisher;

    NetconfCapabilityMonitoringService(final NetconfOperationServiceFactory netconfOperationProvider) {
        this.netconfOperationProvider = netconfOperationProvider;
        netconfOperationProvider.registerCapabilityListener(this);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    synchronized Schemas getSchemas() {
        try {
            return transformSchemas(netconfOperationProvider.getCapabilities());
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException("Exception while closing", e);
        }
    }

    synchronized String getSchemaForModuleRevision(final String moduleName, final Optional<String> revision) {

        Map<String, String> revisionMapRequest = mappedModulesToRevisionToSchema.get(moduleName);
        Preconditions.checkState(revisionMapRequest != null,
                "Capability for module %s not present, available modules : %s",
                moduleName, Collections2.transform(capabilities.values(), CAPABILITY_TO_URI));

        if (revision.isPresent()) {
            String schema = revisionMapRequest.get(revision.get());

            Preconditions.checkState(schema != null,
                    "Capability for module %s:%s not present, available revisions for module: %s", moduleName,
                    revision.get(), revisionMapRequest.keySet());

            return schema;
        }

        Preconditions.checkState(revisionMapRequest.size() == 1,
                "Expected 1 capability for module %s, available revisions : %s", moduleName,
                revisionMapRequest.keySet());
        //Only one revision is present, so return it
        return revisionMapRequest.values().iterator().next();
    }

    private void updateCapabilityToSchemaMap(final Set<Capability> added, final Set<Capability> removed) {
        for (final Capability cap : added) {
            if (!isValidModuleCapability(cap)) {
                continue;
            }

            final String currentModuleName = cap.getModuleName().get();
            Map<String, String> revisionMap = mappedModulesToRevisionToSchema.get(currentModuleName);
            if (revisionMap == null) {
                revisionMap = Maps.newHashMap();
                mappedModulesToRevisionToSchema.put(currentModuleName, revisionMap);
            }

            final String currentRevision = cap.getRevision().get();
            revisionMap.put(currentRevision, cap.getCapabilitySchema().get());
        }
        for (final Capability cap : removed) {
            if (!isValidModuleCapability(cap)) {
                continue;
            }
            final Map<String, String> revisionMap = mappedModulesToRevisionToSchema.get(cap.getModuleName().get());
            if (revisionMap != null) {
                revisionMap.remove(cap.getRevision().get());
                if (revisionMap.isEmpty()) {
                    mappedModulesToRevisionToSchema.remove(cap.getModuleName().get());
                }
            }
        }
    }

    private static boolean isValidModuleCapability(final Capability cap) {
        return cap.getModuleName().isPresent()
                && cap.getRevision().isPresent()
                && cap.getCapabilitySchema().isPresent();
    }


    synchronized Capabilities getCapabilities() {
        return new CapabilitiesBuilder().setCapability(Lists.newArrayList(capabilities.keySet())).build();
    }

    synchronized AutoCloseable registerListener(final NetconfMonitoringService.CapabilitiesListener listener) {
        listeners.add(listener);
        listener.onCapabilitiesChanged(getCapabilities());
        listener.onSchemasChanged(getSchemas());
        return () -> {
            synchronized (NetconfCapabilityMonitoringService.this) {
                listeners.remove(listener);
            }
        };
    }

    private static Schemas transformSchemas(final Set<Capability> caps) {
        final List<Schema> schemas = new ArrayList<>(caps.size());
        for (final Capability cap : caps) {
            if (cap.getCapabilitySchema().isPresent()) {
                final SchemaBuilder builder = new SchemaBuilder();

                Preconditions.checkState(isValidModuleCapability(cap));

                builder.setNamespace(new Uri(cap.getModuleNamespace().get()));

                final String version = cap.getRevision().get();
                builder.setVersion(version);

                final String identifier = cap.getModuleName().get();
                builder.setIdentifier(identifier);

                builder.setFormat(Yang.class);

                builder.setLocation(transformLocations(cap.getLocation()));

                builder.setKey(new SchemaKey(Yang.class, identifier, version));

                schemas.add(builder.build());
            }
        }

        return new SchemasBuilder().setSchema(schemas).build();
    }

    private static List<Schema.Location> transformLocations(final Collection<String> locations) {
        if (locations.isEmpty()) {
            return NETCONF_LOCATIONS;
        }

        final Builder<Schema.Location> b = ImmutableList.builder();
        b.add(NETCONF_LOCATION);

        for (final String location : locations) {
            b.add(new Schema.Location(new Uri(location)));
        }

        return b.build();
    }

    private static Set<Capability> setupCapabilities(final Set<Capability> caps) {
        Set<Capability> capabilities = new HashSet<>(caps);
        capabilities.add(CANDIDATE_CAPABILITY);
        // TODO rollback on error not supported EditConfigXmlParser:100
        // [RFC6241] 8.5.  Rollback-on-Error Capability
        // capabilities.add(new BasicCapability("urn:ietf:params:netconf:capability:rollback-on-error:1.0"));
        return capabilities;
    }

    @Override
    public synchronized void close() throws Exception {
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

    private void notifyCapabilityChanged(final Capabilities capabilities) {
        for (NetconfMonitoringService.CapabilitiesListener listener : listeners) {
            listener.onCapabilitiesChanged(capabilities);
            listener.onSchemasChanged(getSchemas());
        }
    }


    private static NetconfCapabilityChange computeDiff(final Set<Capability> added, final Set<Capability> removed) {
        final NetconfCapabilityChangeBuilder netconfCapabilityChangeBuilder = new NetconfCapabilityChangeBuilder();
        netconfCapabilityChangeBuilder
                .setChangedBy(new ChangedByBuilder().setServerOrUser(new ServerBuilder().setServer(true).build())
                        .build());
        netconfCapabilityChangeBuilder.setDeletedCapability(Lists.newArrayList(Collections2
                .transform(removed, CAPABILITY_TO_URI)));
        netconfCapabilityChangeBuilder.setAddedCapability(Lists.newArrayList(Collections2
                .transform(added, CAPABILITY_TO_URI)));
        // TODO modified should be computed ... but why ?
        netconfCapabilityChangeBuilder.setModifiedCapability(Collections.emptyList());
        return netconfCapabilityChangeBuilder.build();
    }


    private void onCapabilitiesAdded(final Set<Capability> addedCaps) {
        this.capabilities.putAll(Maps.uniqueIndex(setupCapabilities(addedCaps), CAPABILITY_TO_URI));
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
